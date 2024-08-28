package com.ericsson.nms.rv.core.util.filesystem;

import java.util.List;
import java.util.Map;

import com.ericsson.nms.rv.core.util.FunctionalArea;
import com.ericsson.nms.rv.core.util.filesystem.parser.FileSystemJsonParser;

public class FileSystemHandler {

    private final Map<String, List<String>> fsMap;

    public FileSystemHandler() {
        final FileSystemJsonParser fileSystemJsonParser = new FileSystemJsonParser();
        fsMap = fileSystemJsonParser.parseJson();
    }

    public List<String> getOverflowDirectories(final FunctionalArea fa) {

        return fsMap.get(fa.get());
    }

}
