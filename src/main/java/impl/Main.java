package impl;

public class Main {
    public static void main(String[] args) {
        DriveImplementation dI = new DriveImplementation();
        try
        {
//            String target = "";
//            System.out.println(target.split("[/]+").length);
            String[] test = new String[]{"testiranjeStorage"};
            dI.open(test);
////            dI.makeFile("brat","tepaj.txt",true,true);
////            dI.add("C:/Users/nikol/Desktop/kokokook/folder 1/bratko.txt", "/brat 2/testiranje/",true,true);
////            dI.openDir("/"); // kako hendlujemo kosu crtu na pocetak ili ako ima 2 kose crte, hocemo da ga
//                                     // samo kad radimo check path
//            dI.makeDir("test1");
//            dI.openDir("test1");
//            dI.makeDir("test2");
//            dI.openDir("test2");
//            dI.makeFile("testfile.txt");
//            dI.back();
//            dI.back();
//            dI.makeDir("*/","mapa",true,true);
            dI.move("*/test1/test2/test3/test4","*/test1/test2");
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
}
