package org.operaton.examples.integrationconnectors.connector;

import org.operaton.bpm.engine.delegate.BpmnError;
import org.operaton.connect.impl.AbstractConnector;

public class CurrencyConvertConnector extends AbstractConnector<CurrencyConvertRequest, CurrencyConvertResponse> {

    public static final String ID = "currency-convert";
    public static final String ERROR_RATE_UNAVAILABLE = "RATE_UNAVAILABLE";

    public CurrencyConvertConnector() {
        super(ID);
    }

    @Override
    public CurrencyConvertRequest createRequest() {
        return new CurrencyConvertRequest(this);
    }

    @Override
    public CurrencyConvertResponse execute(CurrencyConvertRequest request) {
        double rate;
        try {
            rate = Double.parseDouble(request.getExchangeRate().trim());
        } catch (NumberFormatException e) {
            throw new BpmnError(ERROR_RATE_UNAVAILABLE, "Exchange rate is unavailable: " + request.getExchangeRate());
        }
        double converted = rate * request.getAmount();
        CurrencyConvertResponse response = new CurrencyConvertResponse();
        response.setConvertedAmount(converted);
        return response;
    }
}
