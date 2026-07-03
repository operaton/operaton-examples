package org.operaton.examples.bpmnmodelapiparse;

import java.util.List;

public record ProcessModelReport(
    String processId,
    String processName,
    List<String> userTaskNames,
    List<String> serviceTaskNames,
    int gatewayCount,
    List<String> endEventNames
) {}
