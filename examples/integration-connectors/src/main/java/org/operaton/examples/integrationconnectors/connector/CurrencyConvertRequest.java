package org.operaton.examples.integrationconnectors.connector;

import org.operaton.connect.impl.AbstractConnectorRequest;
import org.operaton.connect.spi.Connector;
import org.operaton.connect.spi.ConnectorRequest;

public class CurrencyConvertRequest extends AbstractConnectorRequest<CurrencyConvertResponse> {

    @SuppressWarnings("unchecked")
    public CurrencyConvertRequest(Connector<CurrencyConvertRequest> connector) {
        super((Connector<ConnectorRequest<CurrencyConvertResponse>>) (Connector<?>) connector);
    }

    public String getExchangeRate() {
        return (String) getRequestParameter("exchangeRate");
    }

    public double getAmount() {
        return ((Number) getRequestParameter("amount")).doubleValue();
    }

    @Override
    protected boolean isRequestValid() {
        return getRequestParameter("exchangeRate") != null
            && getRequestParameter("amount") != null;
    }
}
