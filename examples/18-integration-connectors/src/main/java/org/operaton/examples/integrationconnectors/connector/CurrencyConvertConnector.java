package org.operaton.examples.integrationconnectors.connector;

import org.operaton.connect.impl.AbstractConnector;

public class CurrencyConvertConnector extends AbstractConnector<CurrencyConvertRequest, CurrencyConvertResponse> {

    public static final String ID = "currency-convert";

    public CurrencyConvertConnector() {
        super(ID);
    }

    @Override
    public CurrencyConvertRequest createRequest() {
        return new CurrencyConvertRequest(this);
    }

    @Override
    public CurrencyConvertResponse execute(CurrencyConvertRequest request) {
        double rate = Double.parseDouble(request.getExchangeRate().trim());
        double converted = rate * request.getAmount();
        CurrencyConvertResponse response = new CurrencyConvertResponse();
        response.setConvertedAmount(converted);
        return response;
    }
}
