package com.aries.kb.util;

import javax.servlet.http.HttpServletRequest;
import java.util.Enumeration;
import java.util.Map;

public class OAMUtil {
    public static void httpHeaderToMap(HttpServletRequest req, Map<String, String> map)
    {
        Enumeration<String> enames;
        enames = req.getHeaderNames();

        while (enames.hasMoreElements())
        {
            String key = enames.nextElement();
            String val = req.getHeader(key);
            map.put(key, val) ;
        }
    }
}