package com.ericsson.nms.rv.core.upgrade;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Date;
import java.util.ArrayList;

import static com.ericsson.nms.rv.core.upgrade.CommonDependencyDowntimeHelper.getStringBuilder;

public class PhysicalDownTimeSeqHelper {
    private static final Logger logger = LogManager.getLogger(PhysicalDownTimeSeqHelper.class);
    private static final String YYYY_MMDD_HHMMSS = "yyyyMMddHHmmss";

    static Map<String, String> addComponentToMapPhysical(String component, Map<String, Object> seqMap) {
        Map<String, String> dependency = new HashMap<String, String>();
        try {
            if (seqMap.containsKey("list")) {
                List<String> downTimePeriodsLt = (List<String>) seqMap.get("list");
                final StringBuilder builder = new StringBuilder();
                if (!downTimePeriodsLt.isEmpty() && downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 == 0) {
                    dependency.put(component, getStringBuilder(downTimePeriodsLt).toString());
                } else if (downTimePeriodsLt.isEmpty()) {
                    CommonDependencyDowntimeHelper.EmptyDependencyMap.put(component, (builder.append("N/A").append(",").append("N/A").append(",").append("0s").append(";")).toString());
                } else if (downTimePeriodsLt.size() > 1 && downTimePeriodsLt.size() % 2 != 0) {
                    dependency.put(component, getStringBuilder(downTimePeriodsLt).toString());
                    CommonDependencyDowntimeHelper.EmptyDependencyMap.put(component, getSingleInterval(downTimePeriodsLt.get(downTimePeriodsLt.size() - 1)).toString());
                } else {
                    CommonDependencyDowntimeHelper.EmptyDependencyMap.put(component, getSingleInterval(downTimePeriodsLt.get(downTimePeriodsLt.size() - 1)).toString());
                }
                if (!downTimePeriodsLt.isEmpty() && !seqMap.get("start").toString().equalsIgnoreCase("")) {
                    CommonDependencyDowntimeHelper.EmptyDependencyMap.put(component, getSingleInterval(seqMap.get("start").toString()).toString());
                }
                logger.warn("dependency in addComponentToMap physical:{} {}", dependency.get(component), component);
                logger.warn("EmptyDependencyMap content in addComponentToMap physical:{} {}", CommonDependencyDowntimeHelper.EmptyDependencyMap.get(component), component);
            }
        } catch (final Exception e) {
            logger.info("exception in addComponentToMapPhysical {}", e.getMessage());
        }
        return dependency;
    }

    private static StringBuilder getSingleInterval(String downTimePeriod) {
        final DateFormat sdf = new SimpleDateFormat(YYYY_MMDD_HHMMSS);
        final DateFormat sdf1 = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        final StringBuilder builder = new StringBuilder();
        try {
            final Date dateTimeInitOffline = sdf.parse(downTimePeriod);
            if (downTimePeriod.contains("-")) {
                builder.append("N/A").append(",").append(sdf1.format(dateTimeInitOffline)).append(",").append("N/A").append(";");
            } else {
                builder.append(sdf1.format(dateTimeInitOffline)).append(",").append("N/A").append(",").append("N/A").append(";");
            }
        } catch (final ParseException e) {
            logger.warn(e.getMessage(), e);
        }
        return builder;
    }


    static ArrayList<String> getCombinedList(List<String> offLineStrings, List<String> onLineStrings) {
        ArrayList<String> list = new ArrayList<>();
        try {
            for (String s : offLineStrings) {
                s = s.concat("-off");
                list.add(s);
            }
            for (String s : onLineStrings) {
                s = s.concat("-on");
                list.add(s);
            }
            list.sort(null);
        } catch (final Exception e) {
            logger.info("exception in getCombinedList {}", e.getMessage());
        }
        return list;
    }

    static Map<String, Object> getModifiedSeq(List<String> list) {
        Map<String, Object> modifeidMap = new HashMap<>();
        try {
            logger.info("original Seq : {}", list.toString());
            int i;
            String start = "";
            do {
                i = getSeq(list);
                if (i != 0 && (i <= list.size() - 1)) {
                    list.remove(i - 1);
                } else if (i == 0) {
                    start = list.get(i);
                    list.remove(i);
                } else if (i == 999) {
                    throw new Exception();
                }
            }
            while (i < list.size());
            logger.info("final modified dt seq {}", list.toString());
            ArrayList<String> modifiedList = new ArrayList<>();
            for (String s : list) {
                s = s.split("-")[0];
                modifiedList.add(s);
            }
            logger.info("modified seq : {}", modifiedList.toString());
            logger.info("only online window available : {}", start);
            modifeidMap.put("list", modifiedList);
            modifeidMap.put("start", start);
        } catch (final Exception e) {
            logger.info("exception in getModifiedSeq {}", e.getMessage());
        }
        return modifeidMap;
    }

    private static int getSeq(final List<String> list) {
        final String OFF = "off";
        try {
            int i;
            for (i = 0; i < list.size(); i += 2) {
                String pres = list.get(i).split("-")[1];
                if (!OFF.equalsIgnoreCase(pres)) {
                    break;
                }
            }
            return i;
        } catch (final Exception e) {
            logger.info("exception in getSeq {}", e.getMessage());
            return 999;
        }
    }
}
