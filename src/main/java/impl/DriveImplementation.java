package impl;


import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.gson.Gson;
import spec.Spec;

import java.io.FileWriter;
import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

public class DriveImplementation extends Spec{

    private File rootFile;
    private Drive service;
    private Map<String,String> mimeTypes;
//    private Map<String,String[]> directories;
//
//    private Map<String, String[]> files;
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
            checkCache = new LinkedList<>();
            loadDirectories();
            currPathNode = tree;
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
        ((NodeComposite)parent).addChildLeaf(fileDrive.getName(),fileDrive.getId());
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
        return ((NodeComposite)tree).getNodeByPath(path.split("[/]+"),0);
    }


    protected boolean checkPathContains(Node parent, String name) throws IOException
    {
//        Node target = path.equals(getCurrPath()) ? currPathNode : ((NodeComposite)tree).getNodeByPath(path.split("[/]+"),0);;
        return ((NodeComposite)parent).children.containsKey(name) ;
        //ovo sto returna null ovde treba da vraca obradjuje gresku
    }

    private void loadDirectories() throws IOException
    {
        tree = new NodeComposite(rootFile.getName(),rootFile.getId());
        ((NodeComposite)tree).populateTree();
    }

    @Override
    public void makeDir(String parPath, String name, boolean checkContains, boolean checkPath) throws IOException {
        Node parent = checkPath ? checkPathExists(parPath) : currPathNode;
        if(checkContains)
            checkPathContains(parent,name);
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Collections.singletonList(parent.id));
        File file = service.files().create(fileMetadata)
                .setFields("id, name")
                .execute();
        ((NodeComposite)parent).addChildComp(file.getName(),file.getId());
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
    public void ls(int i) {
        switch(i)
        {
            case 1:
            {

                break;
            }
        }
    }

    public void delete(String path,boolean checkPath) throws IOException
    {

    }


    private class Node
    {
        protected String name,id;

        private Node(String name,String id)
        {
            this.name = name;
            this.id = id;
        }
    }

    private class NodeComposite extends Node
    {
        private Map<String,Node> children;

        private NodeComposite(String name, String id) {
            super(name,id);
            this.children = new HashMap<>();
        }

        private void addChild(Node child)
        {
            children.put(child.name,child);
        }

        private void addChildLeaf(String name, String id)
        {
            children.put(name,new Node(name,id));
        }

        private void addChildComp(String name, String id)
        {
            children.put(name,new NodeComposite(name,id));
        }

        private void populateTree() throws IOException
        {
            FileList fileList = service.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and " + "'" + this.id+ "' in parents")
                    .setSpaces("drive")
                    .setFields("id, name, parents")
                    .execute();
            for(File file : fileList.getFiles())
            {
                Node child;
                if (file.getMimeType().equals("application/vnd.google-apps.folder"))
                {
                    child = new NodeComposite(file.getName(),file.getId());
                    ((NodeComposite)child).populateTree();
                }
                else
                    child = new Node(file.getName(),file.getId());
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
            return ((NodeComposite)children.get(child)).getNodeByPath(path,ind+1);
        }

        private void removeChild(String path)
        {

        }
    }
}
