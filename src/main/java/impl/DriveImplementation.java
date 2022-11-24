package impl;


import com.google.api.client.http.FileContent;
import com.google.api.client.util.DateTime;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import spec.Directory;
import spec.Properties;
import spec.Spec;
import spec.StorageManager;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class DriveImplementation extends Spec{

    static{
        StorageManager.registerStorage(new DriveImplementation());
    }

    private File rootFile;
    private Drive service;
    private Map<String,String> mimeTypes;

    private Node tree;

    private Node currPathNode;

    public DriveImplementation()
    {
        try{
            service = DriveConn.getDriveService();
            Gson gson = new Gson();
            Reader r = Files.newBufferedReader(Paths.get("src/main/resources/mimetypes.json"));
            mimeTypes = gson.fromJson(r,Map.class);
            rootFile = null;
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Override
    public void create() throws Exception {
        File folderMetadata = new File();
        folderMetadata.setName(getCurrDir().getPath());
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        rootFile = service.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        makeConfigDrive();
        loadDirectories();
        currPathNode = tree;
    }

    @Override
    protected void checkSize(long fileSize) throws Exception{
        if(getCurrDir().getSize() + fileSize > getCurrDir().getSizeLimit())
            throw new Exception("Storage size limit reached.");

    }

    @Override
    protected Directory checkRootExists(String rootDir) throws Exception{
        String rootId = service.files().get("root").setFields("id").execute().getId();
        FileList checkExists = service.files().list().setFields("id, name").setQ(rootId + " in parents and name = "+rootDir).execute();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Gson gson = new Gson();
        Directory dir = null;
        for(File file : checkExists.getFiles())
        {
            FileList findConfig = service.files().list().setFields("id")
                    .setQ(file.getId() + " in parents and name = 'config.json' and mimeType = '" + mimeTypes.get(".json") + "'").execute();
            for(File config : findConfig.getFiles())
            {
                service.files().get(config.getId()).executeMediaAndDownloadTo(outputStream);
                try
                {
                    dir = gson.fromJson(outputStream.toString(), Directory.class);
                    break;
                } catch (Exception ignored)
                {
                }
            }
            if (dir != null) break;
        }
        return dir;
    }

    @Override
    public void makeFile(String dirPath, String s, boolean checkContains, boolean checkPath) throws Exception{
        checkFileNum();
        java.io.File file = new java.io.File("src/main/resources/"+s);
        file.createNewFile();
        add("src/main/resources/"+s,dirPath,checkContains,checkPath);
        file.delete();
    }

    @Override
    public void add(String pathSrc, String dirPath,boolean checkContains, boolean checkPath) throws Exception{
        java.io.File file = new java.io.File(pathSrc);
        checkSize(file.length());
        if(!file.exists())
            throw new Exception("Requested file doesn't exist in file sytem.");
        if(file.isDirectory())
            throw new Exception("Cannot upload directory.");

        String name = getLastDir(pathSrc);
        checkExtension(name);

        Node parent = checkPath ? checkPathExists(dirPath) : currPathNode;
        if(checkContains)
            checkPathContains(parent,name);

        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setParents(Collections.singletonList(parent.id));
        String ext = getFileExt(name);

        FileContent fileContent =
                new FileContent(mimeTypes.containsKey(ext) ? mimeTypes.get(ext) : mimeTypes.get("default"),file);
        File fileDrive = service.files().create(fileMetadata,fileContent).execute();
        ((NodeComposite)parent).addChildLeaf(fileDrive.getName(),fileDrive.getId(),fileDrive.getKind(),fileDrive.getSize(),fileDrive.getModifiedTime(),fileDrive.getCreatedTime());
        incrFileNum();
    }

    @Override
    public void rename(String oldPath, String newName) throws Exception{
        throw new Exception("Not implemented");
    }

    private void makeConfigDrive() throws Exception
    {
        java.io.File file = new java.io.File("src/main/resources/config.json");
        FileWriter fw = new FileWriter(file);
        fw.write(makeConfig());
        fw.close();
        add("src/main/resources/config.json","",false,false);
        file.delete();
    }

    protected Node checkPathExists(String path) throws Exception
    {
        Node start = currPathNode;
        if(path.startsWith("../")) {
            start = tree;
            path = path.substring(3);
        }
        return checkPathExists(start,path);
    }

    protected boolean checkPathContains(Object parent, String name) throws Exception
    {
//        Node target = path.equals(getCurrPath()) ? currPathNode : ((NodeComposite)tree).getNodeByPath(path.split("[/]+"),0);;
        Node parentNode = (Node)parent;
        if(!(parentNode instanceof NodeComposite))
            throw new Exception("Requested path is file.");
        if (((NodeComposite)parent).children.containsKey(name))
            throw new Exception("Requested path already contains file of same name.");
        return true;
        //ovo sto returna null ovde treba da vraca  obradjujegresku
    }

    private void loadDirectories() throws Exception
    {
        tree = new NodeComposite(rootFile.getName(),rootFile.getId(),rootFile.getKind(),rootFile.getSize(),rootFile.getModifiedTime(),rootFile.getCreatedTime());
        ((NodeComposite)tree).populateTree();
    }

    @Override
    public void makeDir(String parPath, String name, boolean checkContains, boolean checkPath) throws Exception {
        Node parent = checkPathExists(parPath);
        if(checkContains)
            checkPathContains(parent,name);
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parent.id));
        File file = service.files().create(fileMetadata)
                .setFields("id, name")
                .execute();
        ((NodeComposite)parent).addChildComp(file.getName(),file.getId(),file.getKind(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
        incrDirNum();
    }


    @Override
    public void openDir(String path) throws Exception {
        setCurrPath(path);
    }

    @Override
    public List<Map<String,Object>> ls(int i, String path) throws Exception{
        //?
        String query = "name";
//        List<spec.Properties> prop = getProperties();
//        if(prop.contains(Properties.DATE))
//            query+=", modifiedTime";
//        if(prop.contains(Properties.READ))
//            query+=", modifiedTime";
//        if(prop.contains(Properties.WRITE))
//            query+=", canEdit";
//        if(prop.contains(Properties.LENGTH))
//            query+=", size";
//        if(prop.contains(Properties.TYPE))
//            query+=", kind";
        List<Node> target = null;
        List<Map<String,Object>> result = null;
        switch(i)
        {
            case 1:
            {
                target = ((NodeComposite)currPathNode).getChildren();
                break;
            }
            case 2:
            {
                target = ((NodeComposite)checkPathExists(path)).getChildren();
                //todo: ovde treba da vidimo da kako ce tretiramo adrese, po ovom trenutno sto pis
                // e i guess da se radi apsolutno
                break;
            }
            case 3:
            {
                target = ((NodeComposite)checkPathExists(path)).getChildrenRec();
                break;
            }
            case 4:
            {
                target = ((NodeComposite)tree).getChildrenRec();
                target = target.stream()
                        .filter(x -> x.name.endsWith(path))
                        .collect(Collectors.toList());
                break;
            }
            case 5:
            {
                target = ((NodeComposite)tree).getChildrenRec();
                target = target.stream()
                        .filter(x -> x.name.contains(path))
                        .sorted()
                        .collect(Collectors.toList());
                break;
            }
        }
        if (target.isEmpty())
            throw new Exception("No files with requstesd criteria.");
        result = new ArrayList<>();
        Map<String,Object> map = null;
        for(Node node : target)
        {
            map = new HashMap<>();
            map.put("name",node.name);
            map.put("type",node.type);
            map.put("dateModified",node.dateModified);
            map.put("dateCreated",node.dateCreated);
            map.put("size",node.size);
            result.add(map);
        }
        result.sort(getComparator());
        return result;
    }

    @Override
    public void move(String path,String destPath) throws Exception{
        String[] pathSplit = path.split("[/]+");
        NodeComposite targetPar = (NodeComposite)checkPathExistsParent(pathSplit);
        boolean flag = true;
        try
        {
            flag = checkPathContains(targetPar,pathSplit[pathSplit.length-1]);
        }
        catch (Exception e){}
        if(flag == false)
            return;
        Node destNode = checkPathExists(destPath);
        if(!(destNode instanceof NodeComposite))
            throw new Exception("Destination path is file.");
        Node target = targetPar.removeChild(pathSplit[pathSplit.length - 1]);
        ((NodeComposite)destNode).addChild(target);
        File file  = service.files().get(target.id).setFields("parents").execute();
        String prevParent = file.getParents().get(0);
        file = service.files().update(target.id, null).setAddParents(destNode.id).setRemoveParents(prevParent).setFields("id, parents").execute();
    }

    public void delete(String path, boolean checkPath) throws Exception
    {
//        String pathSplit = path.substring(0,path.lastIndexOf("/"));
//        String childStr = path.substring(path.lastIndexOf("/")+1);
//        String pathChild = path.substring(path.lastIndexOf('/'));

        String[] pathSplit = path.split("[/]+");
        NodeComposite targetPar = (NodeComposite)checkPathExistsParent(pathSplit);
        //if(path.startsWith(".../"))
        boolean flag = true;
        try
        {
            flag = checkPathContains(targetPar,pathSplit[pathSplit.length-1]);
        }
        catch (Exception ignored){}
        if(!flag)
            return;
        if(getCurrPath().startsWith(path)) //ako nam putanje pocinju sa . ovo bi ukljucilo i takve situacije
        {
            if(path.equals(".."))
                throw new Exception("Can't delete storage.");
            setCurrPath(path.substring(0,path.lastIndexOf('/')));
        }
        Node target = targetPar.removeChild(pathSplit[pathSplit.length - 1]);
        service.files().delete(target.id).execute();
    }

    private Node checkPathExistsParent(String[] path) throws Exception
    {
        Node start = path[0].equals("..") ? tree : currPathNode;
        Node result = ((NodeComposite)start).getParentFromPath(path,0);
        if (result == null)
            throw new Exception("Invalid path: " + String.join("/",path));
        return result;
    }

    private Node checkPathExists(Node start, String path) throws Exception
    {
        Node result = ((NodeComposite)start).getNodeByPath(path.split("[/]+"),0);
        if(result == null)
            throw new Exception("Requested path: " + path + " doesn't exist.");
        return result;
    }

    private class Node
    {
        protected String name,id,type;
        protected long size;
        protected DateTime dateModified, dateCreated;

        public Node(String name, String id, String type,long size, DateTime dateModified, DateTime dateCreated) {
            this.name = name;
            this.id = id;
            this.type = type;
            this.size = size;
            this.dateModified = dateModified;
            this.dateCreated = dateCreated;
        }

        public boolean isDirectory()
        {
            return false;
        }
    }

    private class NodeComposite extends Node
    {
        private Map<String,Node> children;

        public NodeComposite(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated) {
            super(name, id, type, size, dateModified, dateCreated);
            this.children = new HashMap<>();
        }

        private void addChild(Node child)
        {
            children.put(child.name,child);
        }

        private void addChildLeaf(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new Node(name,id,type,size,dateModified,dateCreated));
        }

        private void addChildComp(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new NodeComposite(name,id,type,size,dateModified,dateCreated));
        }

        private void populateTree() throws Exception
        {
            FileList fileList = service.files().list()
                    .setQ(this.id+ "' in parents")
                    .setSpaces("drive")
                    .setFields("id, name, modifiedTime, createdTime, kind, size")
                    .execute();
            for(File file : fileList.getFiles())
            {
                Node child;
                if (file.getMimeType().equals("application/vnd.google-apps.folder"))
                {
                    child = new NodeComposite(file.getName(),file.getId(),file.getKind(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
                    ((NodeComposite)child).populateTree();
                }
                else
                    child = new Node(file.getName(),file.getId(),file.getKind(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
                addChild(child);
            }
        }

        private Node getNodeByPath(String[] path, int ind)
        {
            if(ind == path.length)
                return this;
            Node child = children.getOrDefault(path[ind],null);
            if(!(child instanceof NodeComposite))
                return null;
            return ((NodeComposite)child).getNodeByPath(path,ind+1);
        }

        private List<Node> getChildrenComposite()
        {
            return children.values().stream()
                    .filter(x-> x instanceof NodeComposite)
                    .collect(Collectors.toList());
        }

        private List<Node> getChildren()
        {
            return new ArrayList<>(children.values());
        }

        private List<Node> getChildrenLeaf()
        {
            return children.values().stream()
                    .filter(x-> !(x instanceof NodeComposite))
                    .collect(Collectors.toList());
        }

        private List<Node> getChildrenRec()
        {
            Queue<Node> q = new LinkedList<>();
            List<Node> result = new ArrayList<>();
            NodeComposite currNode = this;
            List<Node> childrenLeaf = null;
            List<Node> childrenComp = null;

            while(currNode != null) {
                childrenLeaf = currNode.children.values().stream()
                        .filter(x-> !(x instanceof NodeComposite))
                        .collect(Collectors.toList());
                childrenComp = currNode.children.values().stream()
                        .filter(x-> x instanceof NodeComposite)
                        .collect(Collectors.toList());
                result.addAll(childrenLeaf);
                q.addAll(childrenComp);
                currNode = (NodeComposite) q.poll();
            }
            return result;
        }

        private Node getParentFromPath(String[] path,int ind)
        {
            Node child = children.getOrDefault(path[ind],null);
            if(!(child instanceof NodeComposite))
                return null;
            if(path.length == 1 || ind == path.length - 2)
                return this;
            return ((NodeComposite)child).getParentFromPath(path,ind+1);
        }

        private Node getChild(String name)
        {
            return children.get(name);
        }

        private Node removeChild(String path)
        {
            return children.remove(path);
        }

        @Override
        public boolean isDirectory() {
            return true;
        }
    }
}
