package org.operaton.examples.integrationconnectors.connector;

import org.operaton.connect.spi.Connector;
import org.operaton.connect.spi.ConnectorProvider;

public class CurrencyConvertConnectorProvider implements ConnectorProvider {

    @Override
    public String getConnectorId() {
        return CurrencyConvertConnector.ID;
    }

    @Override
    public Connector<?> createConnectorInstance() {
        return new CurrencyConvertConnector();
    }
}
