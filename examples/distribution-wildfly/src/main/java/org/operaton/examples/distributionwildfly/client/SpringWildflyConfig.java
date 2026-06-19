package org.operaton.examples.distributionwildfly.client;

import org.operaton.bpm.engine.ProcessEngine;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.jndi.JndiObjectFactoryBean;

import javax.naming.NamingException;

/**
 * Spring @Configuration alternative to applicationContext.xml.
 * Looks up the Operaton process engine from WildFly's JNDI registry.
 */
@Configuration
@ComponentScan(basePackageClasses = SpringWildflyConfig.class)
public class SpringWildflyConfig {

    @Bean
    public ProcessEngine processEngine() throws NamingException {
        JndiObjectFactoryBean jndi = new JndiObjectFactoryBean();
        jndi.setJndiName("java:global/operaton-bpm-platform/process-engine/default");
        jndi.setExpectedType(ProcessEngine.class);
        jndi.afterPropertiesSet();
        return (ProcessEngine) jndi.getObject();
    }
}
