package com.ericsson.nms.rv.core.util.filesystem.parser;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import net.minidev.json.JSONArray;
import net.minidev.json.JSONObject;
import net.minidev.json.JSONValue;

public class FileSystemJsonParser {

    private static final Logger logger = LogManager.getLogger(FileSystemJsonParser.class);

    public Map<String, List<String>> parseJson() {

        final Map<String, List<String>> map = new HashMap<>();

        final String file = "/json/fileSystemPaths.json";
        try (final InputStream inputStream = FileSystemJsonParser.class.getResourceAsStream(file)) {

            final Object parse = JSONValue.parse(inputStream);

            if (parse instanceof JSONObject) {

                final JSONObject jsonObject = (JSONObject) parse;

                final Set keys = jsonObject.keySet();
                final Iterator it = keys.iterator();

                while (it.hasNext()) {

                    final String hostname = (String) it.next();

                    final Object responseDto = jsonObject.get(hostname);

                    if (responseDto instanceof JSONArray) {
                        final JSONArray jsonArray = (JSONArray) responseDto;

                        final List<String> directories = new ArrayList<>();

                        for (int index = 0; index < jsonArray.size(); index++) {
                            final String dir = (String) jsonArray.get(index);
                            directories.add(dir);

                        }
                        map.put(hostname, directories);
                    }

                }
            }
        } catch (final IOException e) {
            logger.error("error on parsing the file {}, error: ", file, e);
        }
        return map;
    }

}
