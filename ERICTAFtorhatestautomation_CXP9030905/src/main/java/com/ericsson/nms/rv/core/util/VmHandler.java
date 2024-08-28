package com.ericsson.nms.rv.core.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.nms.rv.core.util.filesystem.parser.FileSystemJsonParser;

public class VmHandler {

    private static final Logger logger = LogManager.getLogger(VmHandler.class);

    private final Map<String, List<String>> vmsByFa;
    private final Map<String, List<String>> fsMap;

    public VmHandler() {
        vmsByFa = new VmByFunctAreaParser().parseJson();
        final FileSystemJsonParser fileSystemJsonParser;
        fileSystemJsonParser = new FileSystemJsonParser();
        fsMap = fileSystemJsonParser.parseJson();
    }

    public Host getVMfromList(final List<Host> hosts, final String type) {
        for (final Host host : hosts) {
            if (host.getHostname().contains(type)) {
                return host;
            }
        }
        return null;
    }

    public List<Object[]> addVmsForAFunctionalArea(final List<Host> virtualMachines, final FunctionalArea functionalArea) {

        final List<String> vmsList = vmsByFa.get(functionalArea.get());
        final List<Object[]> auxVmList = new ArrayList<>();

        for (int index = 0; index < vmsList.size(); index++) {

            final String vmName = vmsList.get(index);
            final List<String> fsList = fsMap.get(vmName);

            if (fsList == null || fsList.isEmpty()) {
                logger.info("no directories configured for VM: {} Funcitonal area: {}", vmName, functionalArea.get());
                break;
            }

            final Host auxVm = getVMfromList(virtualMachines, vmsList.get(index));

            if (auxVm != null) {

                for (final String vmFS : fsList) {
                    auxVmList.add(new Object[]{auxVm, vmFS});
                }
            }
        }
        return auxVmList;
    }

}
