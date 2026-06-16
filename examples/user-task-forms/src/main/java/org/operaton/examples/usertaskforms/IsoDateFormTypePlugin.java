package org.operaton.examples.usertaskforms;

import org.operaton.bpm.engine.impl.cfg.ProcessEngineConfigurationImpl;
import org.operaton.bpm.engine.impl.form.type.DateFormType;
import org.operaton.bpm.spring.boot.starter.util.SpringBootProcessEnginePlugin;
import org.springframework.stereotype.Component;

import java.util.ArrayList;

/**
 * Replaces the default date form type (dd/MM/yyyy) with ISO 8601 (yyyy-MM-dd),
 * so that date fields in task forms accept and return ISO-formatted date strings.
 */
@Component
public class IsoDateFormTypePlugin extends SpringBootProcessEnginePlugin {

    @Override
    public void preInit(ProcessEngineConfigurationImpl configuration) {
        if (configuration.getCustomFormTypes() == null) {
            configuration.setCustomFormTypes(new ArrayList<>());
        }
        configuration.getCustomFormTypes().add(new DateFormType("yyyy-MM-dd"));
    }
}
