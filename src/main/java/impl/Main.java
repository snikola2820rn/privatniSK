package impl;

import org.apache.commons.lang3.StringUtils;
import spec.Properties;
import spec.Spec;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

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
//            dI.move("*/test1/test2/test3/test4","*/test1/test2");
            dI.sort("size", true);
            dI.filter("size");
            dI.filter("createdtime");
            dI.filter("modifiedtime");
            dI.filter("type");
            dI.sort("name",true);
            dI.unfilter("size");
            dI.unfilter("createdtime");
            dI.sort("size",true);

            Map<String, List<Map<Properties, Object>>> result = dI.ls("*",1,true);


        } catch (Exception e)
        {
            System.out.println(e.getMessage());
        }
    }

    public void printLs(Map<String, List<Map<Properties, Object>>> result, Spec dI)
    {
        int columnWidth = 25;
        System.out.println();
        System.out.print(StringUtils.repeat(' ',8));
        for(Properties prop : dI.getProperties())
        {
            if(prop == Properties.ISDIRECTORY)
                continue;
            System.out.print(prop);
            System.out.print(StringUtils.repeat(' ',columnWidth - prop.toString().length()));
        }
        System.out.println();
        for(String key : result.keySet())
        {
            System.out.println(key + ":");
            for(Map<Properties,Object> file : result.get(key))
            {
                System.out.print(StringUtils.repeat(' ',7));

                if(dI.getProperties().contains(Properties.ISDIRECTORY) && file.get(Properties.ISDIRECTORY).equals(true))
                    System.out.print("+");
                else
                    System.out.print(" ");
                for(Properties prop : dI.getDefaultPropOrder())
                {
                    Object target = file.get(prop);
                    if(target == null)
                        continue;
                    if(prop == Properties.SIZE)
                    {
                        Long si = ((Long)target);
                        int num = 0;
                        while(si/1024 > 0)
                        {
                            num++;
                            si /=1024;
                        }
                        System.out.print(si + " ");
                        switch(num)
                        {
                            case 0: {
                                System.out.print(" B");
//                                    System.out.print(StringUtils.repeat(' ', 16 - (si.toString().length() + 2)));
                                break;
                            }
                            case 1: {
                                System.out.print("kB");
//                                    System.out.print(StringUtils.repeat(' ', 16 - (si.toString().length() + 3)));
                                break;
                            }
                            case 2: {
                                System.out.print("MB");
//                                    System.out.print(StringUtils.repeat(' ', 16 - (si.toString().length() + 3)));
                                break;
                            }
                            case 3: {
                                System.out.print("GB");
//                                    System.out.print(StringUtils.repeat(' ', 16 - (si.toString().length() + 3)));
                                break;
                            }
                        }
                        System.out.print(StringUtils.repeat(' ', 25 - (si.toString().length() + 3)));
                    }
                    else
                        System.out.print(target.toString()+StringUtils.repeat(' ',25 - target.toString().length()));
                }
                System.out.println();
            }
        }
    }

}
