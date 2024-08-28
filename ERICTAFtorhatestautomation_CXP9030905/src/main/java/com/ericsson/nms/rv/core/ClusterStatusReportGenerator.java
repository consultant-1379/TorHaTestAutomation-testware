package com.ericsson.nms.rv.core;

import com.ericsson.cifwk.taf.annotations.Attachment;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;

public class ClusterStatusReportGenerator {
    private static final Logger logger = LogManager.getLogger(ClusterStatusReportGenerator.class);
    private static final TreeMap<String, String> hostMap = new TreeMap<>();
    private static final TreeMap<String, String> dataMap = new TreeMap<>();

    public ClusterStatusReportGenerator(final Map<String, String> reportMap, final Map<String, String> data) {
        hostMap.putAll(reportMap);
        dataMap.putAll(data);
    }

    @Attachment(type = "text/html", value = "Cluster Status Report")
    public byte[] attachTxt() {
        final String htmlReport = AvailabilityReportGenerator.processTemplate(AvailabilityReportGenerator.getTemplate("ha/ClusterReport.html"), addClusterReportData());
        return htmlReport.getBytes();
    }

    private Map<String, String> addClusterReportData() {
        logger.info("Adding cluster report ...");
        final Map<String, String> values = new ConcurrentHashMap<>();
        try {
            values.put("addClusterReport", addFile());
            values.put("addClusterDataPopUp", addPopupData());
        } catch (final Exception e) {
            logger.info("Exception in getting health report data : {}", e.getMessage());
        }
        return values;
    }

    private String addFile() {
        final StringBuilder htmlValues = new StringBuilder();
        hostMap.forEach((cluster, hostName) -> {
            htmlValues.append("<tr>");
            htmlValues.append("<td>" + cluster.toUpperCase() + "</td>");
            htmlValues.append("<td style=\"cursor:pointer;\"> <a onclick="+ cluster.replace("-","") +"Function()><u>" + hostName + "</u> </a> </td>");
            htmlValues.append("</tr>");
        });
        return htmlValues.toString();
    }

    private String addPopupData() {
        final StringBuilder functionData = new StringBuilder();
        dataMap.forEach((cluster, data)-> {
            functionData.append("function " + cluster.replace("-","") + "Function() {\n");
            functionData.append("cleanStart();\n");

            try {
                functionData.append("text = \"<p>" + data.replace("\"", "'").replace(" ", "&nbsp;").replace("\n", "<br>") + "</p>\";\n");
            } catch (final Exception e) {
                e.printStackTrace();
            }
            functionData.append("title = \"Cluster : "+ cluster.toUpperCase() +"\";\n");
            functionData.append("backGround = \"#fbffbd\";\n");
            functionData.append("myFunction();\n");
            functionData.append("}\n\n");
        });
        return functionData.toString();
    }
}
