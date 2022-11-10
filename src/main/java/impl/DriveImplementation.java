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

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class DriveImplementation extends Spec{

    private File rootFile;
    private Drive service;
    private Map<String,String> mimeTypes;
    private Queue<String> checkCache;

    private Node tree;

    private Node currPathNode;
    private Node prevPathNode;

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
    public void create() throws IOException {
        File folderMetadata = new File();
        folderMetadata.setName(getCurrDir().getPath());
        folderMetadata.setMimeType("application/vnd.google-apps.folder");
        rootFile = service.files().create(folderMetadata)
                .setFields("id, name")
                .execute();
        makeConfigDrive();
        checkCache = new LinkedList<>();
        loadDirectories();
        currPathNode = tree;
    }

    @Override
    protected Directory checkRootExists(String rootDir) throws IOException{
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
    public void makeFile(String dirPath, String s, boolean checkContains, boolean checkPath) throws IOException{
        java.io.File file = new java.io.File("src/main/resources/"+s);
        file.createNewFile();
        add("src/main/resources/"+s,dirPath,checkContains,checkPath);
        file.delete();
    }

    @Override
    public void add(String pathSrc, String dirPath,boolean checkContains, boolean checkPath) throws IOException{
        java.io.File file = new java.io.File(pathSrc);
        if(!file.exists() || file.isDirectory())
            return;

        String name = getLastDir(pathSrc);
        Node parent = checkPathExists(dirPath);
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
    }

    private void makeConfigDrive() throws IOException
    {
        java.io.File file = new java.io.File("src/main/resources/config.json");
        FileWriter fw = new FileWriter(file);
        fw.write(makeConfig());
        fw.close();
        add("src/main/resources/config.json","",false,false);
        file.delete();
    }

    protected Node checkPathExists(String path) throws IOException
    {
        Node start = currPathNode;
        if(path.startsWith("../")) {
            start = tree;
            path = path.substring(3);
        }
        return checkPathExists(start,path);
    }


    protected boolean checkPathContains(Node parent, String name) throws IOException
    {
//        Node target = path.equals(getCurrPath()) ? currPathNode : ((NodeComposite)tree).getNodeByPath(path.split("[/]+"),0);;
        return ((NodeComposite)parent).children.containsKey(name) ;
        //ovo sto returna null ovde treba da vraca  obradjujegresku
    }

    private void loadDirectories() throws IOException
    {
        tree = new NodeComposite(rootFile.getName(),rootFile.getId(),rootFile.getKind(),rootFile.getSize(),rootFile.getModifiedTime(),rootFile.getCreatedTime());
        ((NodeComposite)tree).populateTree();
    }

    @Override
    public void makeDir(String parPath, String name, boolean checkContains, boolean checkPath) throws IOException {
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
    }


    @Override
    public void openDir(String path) throws IOException {
        currPathNode = checkPathExists(path);
        setPrevPath(getCurrPath());
        setCurrPath(path);
        getSelected().clear();
    }

    @Override
    protected void setCurrPath(String currPath) throws IOException {
        super.setCurrPath(currPath);
        //mozemo da kazemo ovde
        currPathNode = checkPathExists(currPath);
    }

    @Override
    public void search(int i) {
        switch(i)
        {
            case 1:
            {

                break;
            }
        }
    }

    @Override
    public List<Map<String,Object>> ls(int i, String path) throws IOException{
        //?
        String query = "name";
        List<spec.Properties> prop = getProperties();
        if(prop.contains(Properties.DATE))
            query+=", modifiedTime";
        if(prop.contains(Properties.READ))
            query+=", modifiedTime";
        if(prop.contains(Properties.WRITE))
            query+=", canEdit";
        if(prop.contains(Properties.LENGTH))
            query+=", size";
        if(prop.contains(Properties.TYPE))
            query+=", kind";
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
                target = ((NodeComposite)checkPathExists(getCurrPath()+"/"+path)).getChildren();
                break;
            }
            case 3:
            {
                target = service.files().list().setFields(query)
                        .setQ(checkPathExists(path).id + " in parents")
                        .execute();
                break;
            }
            case 4:
            {
                target = service.files().list().setFields(query)
                                .setQ(createQueryRec(path,false))
                                .execute();
                break;
            }
            case 5:
            {
                target = service.files().list().setFields(query)
                        .setQ(createQueryRec(path,true))
                        .execute();
                break;
            }
        }
        if(result != null)
            return result;
        return result;
    }

    private String createQueryRec(String path, boolean limit) throws IOException
    {
        StringBuilder result = new StringBuilder();
        Node start = path == null ? currPathNode : checkPathExists(path);
        result.append(start.id);
        result.append(" in parents");
        List<Node> nodes = limit ? ((NodeComposite)start).getChildrenComposite() : ((NodeComposite)start).getChildrenRec();
        for(Node node : nodes)
        {
            result.append(" and ");
            result.append(node.id);
            result.append(" in parents");
        }
        return result.toString();
    }


    public void delete(String path,boolean checkPath) throws IOException
    {
        String pathSplit = path.substring(0,path.lastIndexOf("/"));
        String childStr = path.substring(path.lastIndexOf("/")+1);
        Node parent = checkPathExists(parentStr);
        Node child = checkPathExists(parent,childStr);
        //if(path.startsWith(".../"))
            if(getCurrPath().startsWith(path.substring(4)))
                setCurrPath(parentStr);
        service.files().delete(child.id).execute();
        ((NodeComposite)parent).removeChild(child.name);
    }

    private Node checkPathExists(Node start, String path)
    {
        return ((NodeComposite)start).getNodeByPath(path.split("[/]+"),0);
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

        private void populateTree() throws IOException
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
            List<Node> children = null;

            while(currNode != null) {
                children = currNode.children.values().stream()
                        .filter(x-> x instanceof NodeComposite)
                        .collect(Collectors.toList());
                result.addAll(children);
                q.addAll(children);
                currNode = (NodeComposite) q.poll();
            }
            return result;
        }

        private Node removePathFromParent(String[] path,int ind)
        {
            if(ind == path.length - 1)
            {
                children.remove(path[path.length - 1]);
                return this;
            }

        }

        private Node getChild(String name)
        {
            return children.get(name);
        }

        private void removeChild(String path)
        {
            children.remove(path);
        }
    }
}
