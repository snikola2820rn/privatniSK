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

//    static{
//        StorageManager.registerStorage(new DriveImplementation());
//    }

    private File rootFile;
    private Drive service;
    private Map<String,String> mimeTypes;

    private String configId;
    
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
        folderMetadata.setName(currDir.getPath());
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        rootFile = service.files().create(folderMetadata)
                .setFields("id, name, mimeType, size, modifiedTime, createdTime")
                .execute();
        makeConfigDrive();
        load();
    }

    @Override
    protected Directory checkRootExists(String rootDir) throws Exception{
        String currId = service.files().get("root").setFields("id").execute().getId();
        FileList checkExists = service.files().list().setQ("'" + currId + "' in parents and name = '"+rootDir +"' and mimeType = 'application/vnd.google-apps.folder'").setFields("files(id, name, mimeType, size, modifiedTime, createdTime)").execute();
//        String[] rootSplit = rootDir.split("[/]+");
//        FileList checkExists = null;
//        for(String dir : rootSplit)
//        {
//            checkExists = service.files().list().setQ("'" + currId + "' in parents and name = '"+dir +"' and mimeType = 'application/vnd.google-apps.folder'").setFields("files(id, name)").execute();
//            if(checkExists.isEmpty())
//            {
//                File fileMetadata = new File();
//                fileMetadata.setName(dir);
//                fileMetadata.setMimeType("application/vnd.google-apps.folder");
//                fileMetadata.setParents(Collections.singletonList(currId));
//                File file = service.files().create(fileMetadata)
//                        .setFields("id, name")
//                        .execute();
//                currId = file.getId();
//                continue;
//            }
//            currId = checkExists.getFiles().get(0).getId();
//        }
//        checkExists = service.files().list().setQ("'" + currId + "' in parents").setFields("files(id, name)").execute();
//        List<File> targets = new ArrayList<>();
//        targets.addAll(checkExists.getFiles());
//        Queue<File> queue = new LinkedList<>();
//        queue.addAll(targets);
//        while(!queue.isEmpty())
//        {
//            File curr = queue.poll();
//            checkExists = service.files().list().setQ("'" + curr.getId() + "' in parents and name = '"+rootDir +"'").setFields("files(id, name)").execute();
//            targets.addAll(checkExists.getFiles());
//            queue.addAll(checkExists.getFiles());
//        }
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        Gson gson = new Gson();
        Directory dir = null;
        for(File file : checkExists.getFiles())
        {
            FileList findConfig = service.files().list()
                    .setQ("'" + file.getId() + "' in parents and name = 'config.json' and mimeType = '" + mimeTypes.get("json") + "'")
                    .setFields("files(id, name)")
                    .execute();
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
            if (dir != null)
            {
                rootFile = file;
                break;
            }
        }
        return dir;
    }

    @Override
    public void makeFile(String dirPath, String s, boolean checkContains, boolean checkPath) throws Exception{
        checkFileNum();
        java.io.File file = new java.io.File("src/main/resources/"+s);
        file.createNewFile();
        try {
            add("src/main/resources/" + s, dirPath, checkContains, checkPath);
        }
        catch(Exception e)
        {
            file.delete();
            throw new Exception(e.getMessage());
        }
        file.delete();
    }

    @Override
    protected void load() throws Exception {
        try {
            loadDirectories();
        }
        catch (Exception e)
        {
            rootFile = null;
            rootObj = null;
            throw e;
        }
        currPathObject = rootObj;
    }

    @Override
    public void add(String pathSrc, String dirPath,boolean checkContains, boolean checkPath) throws Exception{
        checkFileNum();
        java.io.File file = new java.io.File(pathSrc);
        checkSize(file.length());
        if(!file.exists())
            throw new Exception("Requested file doesn't exist in file sytem.");
        if(file.isDirectory())
            throw new Exception("Cannot upload directory.");

        String name = getLastDir(pathSrc);
        checkExtension(name);

        Node parent = checkPath ? checkPathExists(dirPath) : (Node)currPathObject;
        if(checkContains)
            checkPathContains(parent,name);

        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setParents(Collections.singletonList(parent.id));
        String ext = getFileExt(name);

        FileContent fileContent =
                new FileContent(mimeTypes.containsKey(ext) ? mimeTypes.get(ext) : mimeTypes.get("default"),file);
        File fileDrive = service.files().create(fileMetadata,fileContent).setFields("name, id, mimeType, size, createdTime, modifiedTime").execute();
        ((NodeComposite)parent).addChildLeaf(fileDrive.getName(),fileDrive.getId(),fileDrive.getMimeType(),fileDrive.getSize(),fileDrive.getModifiedTime(),fileDrive.getCreatedTime());
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
        File fileMetadata = new File();
        fileMetadata.setName("config.json");
        fileMetadata.setParents(Collections.singletonList(rootFile.getId()));
        FileContent fileContent =
                new FileContent(mimeTypes.get("json"),file);
        File fileDrive = service.files().create(fileMetadata,fileContent).setFields("id").execute();
        configId = fileDrive.getId();
//        add("src/main/resources/config.json","",false,false);
        file.delete();
    }

    protected Node checkPathExists(String path) throws Exception
    {
        Node start = (Node)currPathObject;
        if(path.charAt(0) == '*') {
            start = (Node)rootObj;
            path = path.substring(3);
        }
        return checkPathExists(start,path);
    }

    protected boolean checkPathContains(Object parent, String name) throws Exception
    {
//        Node target = path.equals(getCurrPath()) ? currPathObject : ((NodeComposite)rootObj).getNodeByPath(path.split("[/]+"),0);;
        Node parentNode = (Node)parent;
        if(!parentNode.isDirectory())
            throw new Exception("Requested path is file.");
        if (((NodeComposite)parent).children.containsKey(name))
            throw new Exception("Requested path already contains file of same name.");
        return true;
        //ovo sto returna null ovde treba da vraca  obradjujegresku
    }

    private void loadDirectories() throws Exception
    {
        rootObj = new NodeComposite(null, rootFile.getName(),rootFile.getId(),rootFile.getMimeType(),rootFile.getModifiedTime(),rootFile.getCreatedTime());
        ((NodeComposite)rootObj).populateTree();
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
        ((NodeComposite)parent).addChildComp(file.getName(),file.getId(),file.getMimeType(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
        incrDirNum();
    }


    @Override
    public void openDir(String path) throws Exception {
        setCurrPath(path);
    }

    @Override
    public List<Map<Properties,Object>> ls(int i, String path) throws Exception{
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
        List<Map<Properties,Object>> result = null;
        switch(i)
        {
            case 1:
            {
                target = ((NodeComposite)currPathObject).getChildren();
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
                target = ((NodeComposite)rootObj).getChildrenRec();
                target = target.stream()
                        .filter(x -> x.name.endsWith(path))
                        .collect(Collectors.toList());
                break;
            }
            case 5:
            {
                target = ((NodeComposite)rootObj).getChildrenRec();
                target = target.stream()
                        .filter(x -> x.name.contains(path))
                        .sorted()
                        .collect(Collectors.toList());
                break;
            }
        }
        if (target.isEmpty())
            throw new Exception("No files with requsted criteria.");
        result = new ArrayList<>();
        Map<Properties,Object> map = null;
        for(Node node : target)
        {
            map = new HashMap<>();
            map.put(Properties.NAME,node.name);
            map.put(Properties.CREATEDTIME,node.dateCreated);
            map.put(Properties.MODIFIEDTIME,node.dateModified);
            map.put(Properties.SIZE,node.size);
            map.put(Properties.TYPE,node.type);
            map.put(Properties.ISDIRECTORY,node.isDirectory());
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

    @Override
    protected void moveBack() throws Exception {
        if(((Node)currPathObject).parent == null)
            throw new Exception("Cannot go further back in root directory.");
        currPathObject = ((Node)currPathObject).parent;
        currPath = reconCurrPath();
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
            if(path.equals("*"))
                throw new Exception("Can't delete storage.");
            setCurrPath(path.substring(0,path.lastIndexOf('/')));
        }
        Node target = targetPar.getChild(pathSplit[pathSplit.length - 1]);
        if(target.id.equals(configId))
            throw new Exception("Cannot delete config file of storage.");
//        Node target = targetPar.removeChild(pathSplit[pathSplit.length - 1]);
        targetPar.removeChild(pathSplit[pathSplit.length - 1]);
        service.files().delete(target.id).execute();
    }

    private Node checkPathExistsParent(String[] path) throws Exception
    {
        Node start = path[0].equals("*") ? (Node)rootObj : (Node)currPathObject;
        Node result = ((NodeComposite)start).getParentFromPath(path,0);
        if (result == null)
            throw new Exception("Invalid path: " + String.join("/",path));
        return result;
    }

    private String reconCurrPath()
    {
        if(currPathObject == rootObj)
            return "*";
        String result = ((Node)currPathObject).name;
        NodeComposite curr = ((Node)currPathObject).parent;
        while (curr != null)
        {
            result = curr.name + "/" + result;
            curr = curr.parent;
        }
        return "*/" + result;
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
        protected NodeComposite parent;

        public Node(NodeComposite parent, String name, String id, String type,long size, DateTime dateModified, DateTime dateCreated) {
            this.parent = parent;
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

        public NodeComposite(NodeComposite parent, String name, String id, String type, DateTime dateModified, DateTime dateCreated) {
            super(parent, name, id, type, 0, dateModified, dateCreated);
            this.children = new HashMap<>();
        }

        private void addChild(Node child)
        {
            children.put(child.name,child);
        }

        private void addChildLeaf(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new Node(this,name,id,type,size,dateModified,dateCreated));
        }

        private void addChildComp(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new NodeComposite(this,name,id,type,dateModified,dateCreated));
        }

        private void populateTree() throws Exception
        {
            FileList fileList = service.files().list()
                    .setQ("'"+this.id+ "' in parents")
                    .setSpaces("drive")
                    .setFields("files(id, name, modifiedTime, createdTime, size, mimeType)")
                    .execute();
            for(File file : fileList.getFiles())
            {
                Node child;
                if (file.getMimeType().equals("application/vnd.google-apps.folder"))
                {
                    incrDirNum();
                    child = new NodeComposite(this, file.getName(),file.getId(),file.getMimeType(),file.getModifiedTime(),file.getCreatedTime());
                    ((NodeComposite)child).populateTree();
                }
                else
                {
                    checkSize(file.getSize());
                    checkFileNum();
                    incrFileNum();
                    incrSize(file.getSize());
                    child = new Node(this, file.getName(),file.getId(),file.getMimeType(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
                }
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
