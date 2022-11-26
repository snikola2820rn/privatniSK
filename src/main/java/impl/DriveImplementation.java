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
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

public class DriveImplementation extends Spec{

    static{
        StorageManager.registerStorage(new DriveImplementation());
    }

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
        if(configId==null)
            configId =  ((NodeComposite)rootObj).getChild("config.json").id;
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

        Node parent = checkPath ? checkPathExistsDirectory(dirPath) : (Node)currPathObject;
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
        incrSize(fileDrive.getSize());
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
        if(path == null || path.isEmpty())
            return (Node)currPathObject;
        Node start = (Node)currPathObject;
        List<String> target = new ArrayList<>(Arrays.asList(path.split("[/]+")));
        if(target.size() == 0)
            return start;
        Node result = ((NodeComposite)start).getNodeByPath(target,0);
        if(result == null)
            throw new Exception("Requested path: " + path + " doesn't exist.");
        return result;
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
    }

    private void loadDirectories() throws Exception
    {
        rootObj = new NodeComposite(null, rootFile.getName(),rootFile.getId(),rootFile.getMimeType(),rootFile.getModifiedTime(),rootFile.getCreatedTime());
        ((NodeComposite)rootObj).populateTree();
    }

    @Override
    public void makeDir(String parPath, String name, boolean checkContains, boolean checkPath) throws Exception {
        Node parent = checkPath ? checkPathExistsDirectory(parPath) : (Node)currPathObject;
        if(checkContains)
            checkPathContains(parent,name);
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parent.id));
        File file = service.files().create(fileMetadata)
                .setFields("id, name, mimeType, modifiedTime, createdTime")
                .execute();
        ((NodeComposite)parent).addChildComp(file.getName(),file.getId(),file.getMimeType(),file.getModifiedTime(),file.getCreatedTime());
        incrDirNum();
    }

    @Override
    public Map<String,List<Map<Properties,Object>>> ls(String path,int i, boolean rec,String... args) throws Exception{
        //?
        List<Node> target = rec ? ((NodeComposite)checkPathExistsDirectory(path)).getChildrenRec()
                : ((NodeComposite)checkPathExistsDirectory(path)).getChildren();
        Map<String,List<Map<Properties,Object>>> result;
        switch(i)
        {
            case 1:
            {
                break;
            }
            case 2:
            {
                for(String ext:args)
                {
                    if(!mimeTypes.containsKey(ext))
                        throw new Exception(args[0] + " is not an extension.");
                }
                target = target.stream()
                        .filter(x -> {
                            for(String ext:args)
                            {
                                if(x.name.endsWith(ext))
                                    return true;
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
                break;
            }
            case 3:
            {
                target = target.stream()
                        .filter(x -> {
                            for(String text:args)
                            {
                                if(x.name.contains(text))
                                    return true;
                            }
                            return false;
                        })
                        .collect(Collectors.toList());
                break;
            }
            case 4:
            {
//                LocalDateTime date1 = null;
//                LocalDateTime date2 = null;
//                for(DateTimeFormatter dateTimeFormatter : dateFormats) {
//                    try {
//                        date1 = LocalDateTime.parse(args[0], dateTimeFormatter);
//                        date2 = LocalDateTime.parse(args[1], dateTimeFormatter);
//                        break;
//                    } catch (Exception e) {
//                        continue;
//                    }
//                }
//                if(date1 == null || date2 == null)
//                    throw new Exception("Invalid date format.");
//                target = ((NodeComposite)rootObj).getChildrenRec();
//                target = target.stream()
//                        .filter(x -> {
//                            Instant in = Instant.parse(x.dateCreated.toString());
//                            LocalDateTime xTime = LocalDateTime.ofInstant(in, ZoneId.systemDefault());
//                            return xTime.isAfter(date1) && xTime ;
//                        })
//                        .sorted()
//                        .collect(Collectors.toList());
            }
        }
        if (target.isEmpty())
            throw new Exception("No files with requested criteria.");
        result = new HashMap<>();
        Map<Properties,Object> map = null;
        Map<Node,List<Map<Properties,Object>>> resultTmp  = new HashMap<>();
        for(Node node : target)
        {
            if(!resultTmp.containsKey(node.parent))
                resultTmp.put(node.parent, new ArrayList<>());
            map = new HashMap<>();
            if(getProperties().contains(Properties.NAME))
                map.put(Properties.NAME,node.name);
            if(getProperties().contains(Properties.CREATEDTIME))
                map.put(Properties.CREATEDTIME,LocalDateTime.ofInstant(Instant.parse(node.dateCreated.toString()),ZoneId.systemDefault()).format(dateFormats[2]));
            if(getProperties().contains(Properties.MODIFIEDTIME))
                map.put(Properties.MODIFIEDTIME,LocalDateTime.ofInstant(Instant.parse(node.dateModified.toString()),ZoneId.systemDefault()).format(dateFormats[2]));
            if(getProperties().contains(Properties.SIZE))
                map.put(Properties.SIZE, Long.valueOf(node.size));
            if(getProperties().contains(Properties.TYPE))
                map.put(Properties.TYPE,node.type);
            if(getProperties().contains(Properties.ISDIRECTORY))
                map.put(Properties.ISDIRECTORY,Boolean.valueOf(node.isDirectory()));
            resultTmp.get(node.parent).add(map);
        }
        for(List<Map<Properties,Object>> list : resultTmp.values())
        {
            list.sort(getComparator());
        }
        for(Map.Entry<Node,List<Map<Properties,Object>>> entry : resultTmp.entrySet())
        {
            result.put(reconPathByNode(entry.getKey()),entry.getValue());
        }
        return result;
    }

    @Override
    public void move(String path,String destPath) throws Exception{
        Node target = checkPathExists(path);
        if (target == rootObj)
            throw new Exception("Cannot move root directory.");
        Node destNode = checkPathExistsDirectory(destPath);
        if(target.isDirectory())
        {
            if (target == destNode)
                throw new Exception("Cannot move directory into itself.");
            if (target.parent == destNode)
                throw new Exception("Requested file is already in requested directory.");
            Node curr = destNode.parent;
            while (curr != rootObj) {
                if (curr == target)
                    throw new Exception("Cannot move a parent directory into a child directory.");
                curr = curr.parent;
            }
        }
        NodeComposite targetPar = target.parent;
        targetPar.removeChild(target);
        ((NodeComposite)destNode).addChild(target);
        service.files().update(target.id, null).setAddParents(destNode.id).setRemoveParents(targetPar.id).setFields("id, parents").execute();
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
        Node target = checkPathExists(path);
        if(target == rootObj)
            throw new Exception("Cannot delete storage.");
        if(target.parent == rootObj && Objects.equals(target.name, "config.json"))
            throw new Exception("Cannot delete config file of storage.");
        Node curr = (Node)currPathObject;
        while(curr != rootObj)
        {
            if(curr == target)
            {
                currPathObject = target.parent;
                currPath = reconCurrPath();
                break;
            }
            curr = curr.parent;
        }

        NodeComposite targetPar = target.parent;
        targetPar.removeChild(target);
//        currDir.setSize(currDir.getSize() - target.size);
//        currDir.setFileNum()
        service.files().delete(target.id).execute();
        decrFileNum(target.fileNum);
        decrSize(target.size);
    }

    private Node checkPathExistsParent(String[] path) throws Exception
    {
        Node start = path[0].equals("*") ? (Node)rootObj : (Node)currPathObject;
        Node result = ((NodeComposite)start).getParentFromPath(path,0);
        if (result == null)
            throw new Exception("Invalid path: " + String.join("/",path));
        return result;
    }

//    private void incrSizeParents(long size)
//    {
//        Node curr = (Node)currPathObject;
//        while (curr != null)
//        {
//            curr.size += size;
//            curr = curr.parent;
//        }
//    }
//
//    private void decrSizeParents(long size)
//    {
//        Node curr = (Node)currPathObject;
//        while(curr!=null)
//        {
//            curr.size -= size;
//            curr = curr.parent;
//        }
//    }

    protected Node checkPathExistsDirectory(String path) throws Exception
    {
        Node result = checkPathExists(path);
        if(!result.isDirectory())
            throw new Exception("Requested path is file.");
        return result;
    }

    protected String reconCurrPath()
    {
        if(currPathObject == rootObj)
            return "*";
        StringBuilder result = new StringBuilder(((Node) currPathObject).name);
        NodeComposite curr = ((Node)currPathObject).parent;
        while (curr.parent != null)
        {
            result.insert(0, curr.name + "/");
            curr = curr.parent;
        }
        return "*/" + result;
    }

    private String reconPathByNode(Node node)
    {
        if(node == rootObj)
            return "*";
        StringBuilder result = new StringBuilder(((Node) node).name);
        NodeComposite curr = ((Node)node).parent;
        while (curr.parent != null)
        {
            result.insert(0, curr.name + "/");
            curr = curr.parent;
        }
        return "*/" + result;
    }

    private class Node
    {
        protected String name,id,type;
        protected long size;
        protected DateTime dateModified, dateCreated;
        protected NodeComposite parent;
        protected int fileNum = 1;

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
            this.fileNum = 0;
        }

        private void addChild(Node child)
        {
            children.put(child.name,child);
            if(child.size == 0 && child.fileNum == 0)
                return;
            this.incrSizeRec(child.size,child.fileNum);
        }

        private void addChildPopulate(Node child)
        {
            children.put(child.name,child);
        }

        private void addChildLeaf(String name, String id, String type, long size, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new Node(this,name,id,type,size,dateModified,dateCreated));
            this.incrSizeRec(size,1);
        }

        private void addChildComp(String name, String id, String type, DateTime dateModified, DateTime dateCreated)
        {
            children.put(name,new NodeComposite(this,name,id,type,dateModified,dateCreated));
        }

        private void incrSizeRec(long size, int fileNum)
        {
            this.size += size;
            this.fileNum += fileNum;
            if(this.parent == null)
                return;
            this.parent.incrSizeRec(size, fileNum);
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
                    checkExtension(file.getName());
                    incrFileNum();
                    incrSize(file.getSize());
                    child = new Node(this, file.getName(),file.getId(),file.getMimeType(),file.getSize(),file.getModifiedTime(),file.getCreatedTime());
                }
                addChildPopulate(child);
                this.size+= child.size;
                this.fileNum += child.fileNum;
            }
        }

        private Node getNodeByPath(List<String> path, int ind)
        {
            if(ind == path.size())
                return this;
            Node child = children.getOrDefault(path.get(ind),null);
            if (child == null)
            {
                if (ind == 0) {
                    if(path.get(ind).isEmpty())
                        return this.getNodeByPath(path, ind + 1);
                    if(path.get(ind).length() == 1 && path.get(ind).charAt(0) == '*')
                        return ((NodeComposite)rootObj).getNodeByPath(path,ind+1);
                    return null;
                }
                else
                    return null;
            }
            if(!(child instanceof NodeComposite)) {
                if(ind == path.size() - 1)
                    return child;
                else
                    return null;
            }
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
            Node currNode = this;
            List<Node> childrenLeaf = null;
            List<Node> childrenComp = null;

            while(currNode != null) {
                if(!currNode.isDirectory())
                {
                    currNode =  q.poll();
                    continue;
                }

//                childrenLeaf = currNode.children.values().stream()
//                        .filter(x-> !(x instanceof NodeComposite))
//                        .collect(Collectors.toList());
//                childrenComp = currNode.children.values().stream()
//                        .filter(x-> x instanceof NodeComposite)
//                        .collect(Collectors.toList());
                result.addAll(((NodeComposite)currNode).children.values());
                q.addAll(((NodeComposite)currNode).children.values());
                currNode = q.poll();
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
            Node target = children.remove(path);
            if(target.size == 0 && target.fileNum == 0)
                return target;
            this.decrSizeRec(target.size, target.fileNum);
            return target;
        }

        private void decrSizeRec(long size, int fileNum)
        {
            this.size -= size;
            this.fileNum -= fileNum;
            if(this.parent == null)
                return;
            this.parent.decrSizeRec(size,fileNum);
        }

        public void removeChild(Node child)
        {
            removeChild(child.name);
        }

        private void incrFileNumRec(int num)
        {
            this.fileNum += num;
            if(this.parent == null)
                return;
            this.parent.incrFileNumRec(num);
        }

        private void decrFileNumRec(int num)
        {
            this.fileNum -= num;
            if(this.parent == null)
                return;
            this.parent.decrFileNumRec(num);
        }

        @Override
        public boolean isDirectory() {
            return true;
        }
    }
}
