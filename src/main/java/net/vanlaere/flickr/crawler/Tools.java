package net.vanlaere.flickr.crawler;

import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

/**
 * This class provides some generic helper methods for the Flickr Crawler.
 * @author oliviervanlaere@gmail.com
 */
public class Tools {

    /**
     * Helper method that sorts a Map by its values, not the keys.
     * @param map The Map that needs to be sorted by its values.
     * @return A new map that is sorted based on its values.
     */
    @SuppressWarnings({"unused", "unchecked"})
    public static Map sortByValue(Map map) {
        List list = new LinkedList(map.entrySet());
        Collections.sort(list, new Comparator() {

            @Override
            public int compare(Object o1, Object o2) {
                return ((Comparable) ((Map.Entry) (o1)).getValue()).compareTo(((Map.Entry) (o2)).getValue()) * -1;
            }
        });
        Map result = new LinkedHashMap();
        for (Iterator it = list.iterator(); it.hasNext();) {
            Map.Entry entry = (Map.Entry) it.next();
            result.put(entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Helper method that fills in parameters in a template filename.
     * @param template The String template
     * @param params The parameters to replace the placeholders with
     * @return the adapted String
     */
    public static String applyTemplateValues(String template, String[] params) {
        String filename = template;
        for (int i = 0; i < params.length; i++) {
            if (params[i].length() > 0) {
                filename = filename.replace("@" + (i + 1), params[i]);
            } else {
                filename = filename.replace(".@" + (i + 1), "");
            }
        }
        return filename;
    }
}