package com.ericsson.nms.rv.core;

import com.ericsson.cifwk.taf.annotations.Attachment;
import com.ericsson.nms.rv.core.util.CommonUtils;
import com.ericsson.sut.test.cases.HighAvailabilityTestCase;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;


public class CsvFilesReportGenerator {

    private static final Logger logger = LogManager.getLogger(CsvFilesReportGenerator.class);
    private static  TreeMap<String, String> txtFiles = new TreeMap<>();
    private static  TreeMap<String, String> dataFiles = new TreeMap<>();

    private static final String tafClusterId = HAPropertiesReader.getTafClusterId();
    private static final String tafConfigDitDeploymentName = HAPropertiesReader.getTafConfigDitDeploymentName();


    public CsvFilesReportGenerator(final Map<String, String> txtFilesMap, final Map<String, String> dataFilesMap) {
        txtFiles.putAll(txtFilesMap);
        dataFiles.putAll(dataFilesMap);
    }

    @Attachment(type = "text/html", value = "Infra Down Time Data Files")
    public byte[] attachTxt()
    {
        final String htmlReport = AvailabilityReportGenerator.processTemplate(AvailabilityReportGenerator.getTemplate("ha/CsvAttachments.html"), csvFilesHTMLValues());
        return htmlReport.getBytes();
    }

    private Map<String,String> csvFilesHTMLValues() {
        final Map<String, String> values = new ConcurrentHashMap<>();
        if(!HighAvailabilityTestCase.upgradeFailedFlag) {
            try {
                if(CommonUtils.isFileCopyFailed ){
                    values.put("failedMessage","Some Files Copying Failed check logs for the Cause");
                } else {
                    values.put("failedMessage"," ");
                }
                values.put("addCsvFiles", addCsvFiles(txtFiles));
                values.put("addDataCsvFiles", addCsvFiles(dataFiles));
                values.put("addcsvFileDataPopUp", addPopUp(txtFiles));
                values.put("addFileDataPopUp", addPopUp(dataFiles));
                if (HAPropertiesReader.isEnvCloud()) {
                    values.put("ENV", "cloud");
                } else if(HAPropertiesReader.isEnvCloudNative()){
                    values.put("ENV", "cENM");
                } else {
                    values.put("ENV", "Physical");
                }
                if (!"-".equals(tafClusterId)) {
                    values.put("ENV", tafClusterId);
                } else if (!tafConfigDitDeploymentName.isEmpty()) {
                    values.put("ENV", tafConfigDitDeploymentName);
                } else{
                    values.put("ENV", "cENM");
                }
            } catch (final Exception e) {
                logger.info("Exception in attaching csv files........{}", e.getMessage());
            }
        }
        return values;
    }


    private String addCsvFiles(final TreeMap<String, String> csvMap) {
        final StringBuilder htmlValues = new StringBuilder();
        csvMap.forEach((id, uri) -> {
            htmlValues.append("<tr>");
            htmlValues.append("<td style=\"cursor:pointer;\"> <a onclick="+ id.split("\\.")[0].replace("-","") +"Function()><u>" + id + "</u> </a> </td>");
            htmlValues.append("</tr>");
        });
        return htmlValues.toString();
    }

    private String addPopUp(final TreeMap<String, String> csvMap) {
        StringBuilder functionData = new StringBuilder();
        csvMap.forEach((id, uri)->{
            functionData.append("function "+id.split("\\.")[0].replace("-","") + "Function() {\n");
            functionData.append("cleanStart();\n");

            try {
                if (id.equalsIgnoreCase("endpoint.json")) { // align indent
                    functionData.append("text = \"<p>" + uri.replace("\"", "'").replace(" ", "&nbsp;").replace("\n", "<br>") + "</p>\";\n");
                } else {
                    functionData.append("text = \"<p>" + uri.replace("\"", "'").replace("\n", "<br>") + "</p>\";\n");
                }
            } catch (final Exception e) {
                e.printStackTrace();
            }
            functionData.append("title = \"File "+id+"\";\n");
            functionData.append("backGround = \"#ffe6e6\";\n");
            functionData.append("myFunction();\n");
            functionData.append("}\n\n");
        });
        return functionData.toString();
    }
}
