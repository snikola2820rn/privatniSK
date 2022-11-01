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
    private Map<String,String[]> directories;


    public DriveImplementation()
    {
        try{
            service = DriveConn.getDriveService();
            Gson gson = new Gson();
            Reader r = Files.newBufferedReader(Paths.get("src/main/resources/mimetypes.json"));
            mimeTypes = gson.fromJson(r,Map.class);
            rootFile = null;
            directories = new HashMap<>();
            loadDirectories();
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
    public void makeFile(String dirPath, String s) throws IOException{
        java.io.File file = new java.io.File("src/main/resources/"+s);
        file.createNewFile();
        add("src/main/resources/"+s,dirPath);
        file.delete();
    }

    @Override
    public void add(String pathSrc, String dirPath) throws IOException{
        String name = getLastDir(pathSrc);
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setParents(Collections.singletonList(checkPathExists(dirPath)));
        java.io.File file = new java.io.File(pathSrc);
        String ext = getFileExt(name);
        FileContent fileContent =
                new FileContent(mimeTypes.containsKey(ext) ? mimeTypes.get(ext) : mimeTypes.get("default"),file);
        File fileDrive = service.files().create(fileMetadata,fileContent).execute();
    }

    private void makeConfigDrive() throws IOException
    {
        java.io.File file = new java.io.File("src/main/resources/config.json");
        FileWriter fw = new FileWriter(file);
        fw.write(makeConfig());
        fw.close();
        add("src/main/resources/config.json","");
        file.delete();
    }

    protected String checkPathExists(String path) throws IOException
    {
        String[] folders = path.split("[\\/]+");
        String currId = rootFile.getId();
        for (String folder : folders)
        {
            if(!directories.containsKey(folder))
                return null;
            if(!Objects.equals(currId, directories.get(folder)[1]))
                return null;
            currId = directories.get(folder)[0];
        }
        return currId;
    }


    protected boolean checkPathContains(String path, String name)
    {
        String target = getLastDir(path);
        if()
        return false;
    }

    private void loadDirectories() throws IOException
    {
        String currId = rootFile.getId();
        FileList fileList;

        Queue<File> q = new LinkedList<>();
        while(true) {
            fileList = service.files().list()
                    .setQ("mimeType='application/vnd.google-apps.folder' and " + "'" + currId + "' in parents")
                    .setSpaces("drive")
                    .setFields("id, name, parents")
                    .execute();
            //directories.addAll(fileList.getFiles());
            for(File file : fileList.getFiles())
                directories.put(file.getName(), new String[]{file.getId(),file.getParents().get(0)} );
            q.addAll(fileList.getFiles());
            if(q.isEmpty())
                break;
            currId = q.poll().getId();
        }
        getCurrDir().setDirNum(directories.size());
    }


    @Override
    public void makeDir(String parPath, String name) throws IOException {
        File fileMetadata = new File();
        fileMetadata.setName(name);
        fileMetadata.setMimeType("application/vnd.google-apps.folder");
        fileMetadata.setParents(Arrays.asList(directories.get(getLastDir(parPath))));
        FileList fileList =
    }
}
