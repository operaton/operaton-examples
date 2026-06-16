package org.operaton.examples.integrationconnectors.connector;

import org.operaton.connect.impl.AbstractConnectorResponse;

import java.util.Map;

public class CurrencyConvertResponse extends AbstractConnectorResponse {

    private Double convertedAmount;

    public Double getConvertedAmount() {
        return convertedAmount;
    }

    public void setConvertedAmount(Double convertedAmount) {
        this.convertedAmount = convertedAmount;
    }

    @Override
    protected void collectResponseParameters(Map<String, Object> responseParameters) {
        if (convertedAmount != null) {
            responseParameters.put("convertedAmount", convertedAmount);
        }
    }
}
