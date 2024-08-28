package com.ericsson.nms.rv.core.netsimhandler;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.ericsson.cifwk.taf.data.Host;
import com.ericsson.cifwk.taf.handlers.netsim.NetSimCommandHandler;
import com.ericsson.cifwk.taf.handlers.netsim.domain.NetworkElement;
import com.ericsson.nms.rv.core.util.CliResult;
import com.ericsson.nms.rv.core.util.CliShell;
import com.ericsson.oss.testware.hostconfigurator.HostConfigurator;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;


/**
 * Creates a HaNetsimService
 */
public class HaNetsimHandlerFactory {
    private static final Set<Host> ALL_NETSIM_HOSTS = new HashSet<>(HostConfigurator.getAllNetsimHosts());
    private static final Logger logger = LogManager.getLogger(HaNetsimHandlerFactory.class);
    private static DefaultNetsimRespoProvider defaultNetsimRespoProvider;
    private HaNetsimHandlerFactory() {
    }

    /**
     * Returns a HaNetsimService with all the ne available
     *
     * @return
     */
    public static HaNetsimService createDefaultHaNetsimService() {
        return new HaNetsimService(new DefaultNetsimRespoProvider(NetSimCommandHandler.getInstance(new ArrayList<Host>(ALL_NETSIM_HOSTS))));
    }

    public static HaNetsimService createDefaultHaNetsimService(final List<String> netSimsFromWorkload) {
        final List<Host> netsimList = new ArrayList<>();
        logger.info("netSimsFromWorkload : {}", netSimsFromWorkload);
        for (final String netsim : netSimsFromWorkload) {
            final Host netsimHost = HostConfigurator.getHost(netsim);
            if (netsimHost != null && validateNetsimConnection(netsimHost)) {
                logger.info("netsimHost added : {}", netsimHost.getHostname());
                netsimList.add(netsimHost);
            } else if (netsimHost != null) {
                logger.warn("Validation failed for netsim {}", netsimHost.getHostname());
            } else {
                logger.warn("NetsimHost from HostConfigurator is null.");
                for (Host host: HostConfigurator.getAllNetsimHosts()) {
                    logger.info("Available HostConfigurator Netsim: {}", host.getHostname());
                }
            }
        }
        defaultNetsimRespoProvider = new DefaultNetsimRespoProvider(NetSimCommandHandler.getInstance(netsimList));
        return new HaNetsimService(defaultNetsimRespoProvider);
    }

    public static Map<String, List<NetworkElement>> getNeListCache() {
        return defaultNetsimRespoProvider.getNeListCache();
    }

    public static List<String> getNetsimHosts(final List<String> nodes) {
        final List<String> netsimList = new ArrayList<>();
        for (Host host : ALL_NETSIM_HOSTS) {
            logger.info("Netsim Hosts: {}", host.getHostname());
            if (!validateNetsimConnection(host)) {
                continue;
            }
            try {
                Map<String, List<NetworkElement>> neList = NetSimCommandHandler.getInstance(host).getAllStartedNEs().stream().collect(Collectors.groupingBy(NetworkElement::getNodeType));
                if (!neList.isEmpty()) {
                    neList.forEach((nodeType, nodeList) -> {
                        logger.info("nodeType : {}", nodeType);
                        for (final String node : nodes) {
                            for (final NetworkElement ne : nodeList) {
                                if (node.contains(ne.getName()) && !netsimList.contains(host.getHostname())) {
                                    netsimList.add(host.getHostname());
                                }
                            }
                        }
                    });
                }
            } catch (final Exception e) {
                logger.info("Netsim host error: {}", host.getHostname());
            }
        }
        return netsimList;
    }

    private static boolean validateNetsimConnection(final Host netsimHost) {
        try {
            final CliShell shell = new CliShell(netsimHost);
            final CliResult result = shell.execute("hostname");
            return result.isSuccess();
        } catch (final Exception e) {
            logger.warn("Failed to connect to Netsim : {}", netsimHost.getHostname());
            return false;
        }
    }

}