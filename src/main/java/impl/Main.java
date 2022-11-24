package impl;

public class Main {
    public static void main(String[] args) {
        DriveImplementation dI = new DriveImplementation();
        try
        {
            String[] test = new String[]{"testiranjeStorage"};
            dI.open(test);
//            dI.makeFile("brat","tepaj.txt",true,true);
            dI.add("C:/Users/nikol/Desktop/kokokook/folder 1/bratko.txt", "/brat 2/testiranje/",true,true);
            dI.openDir("brat"); // kako hendlujemo kosu crtu na pocetak ili ako ima 2 kose crte, hocemo da ga
                                     // samo kad radimo check path
        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }
}
