package org.operaton.examples.spinjson;

public class LoanApplication {
    private String applicantName;
    private double amount;
    private int termMonths;
    private String purpose;

    public LoanApplication() {}

    public LoanApplication(String applicantName, double amount, int termMonths, String purpose) {
        this.applicantName = applicantName;
        this.amount = amount;
        this.termMonths = termMonths;
        this.purpose = purpose;
    }

    public String getApplicantName() { return applicantName; }
    public void setApplicantName(String applicantName) { this.applicantName = applicantName; }
    public double getAmount() { return amount; }
    public void setAmount(double amount) { this.amount = amount; }
    public int getTermMonths() { return termMonths; }
    public void setTermMonths(int termMonths) { this.termMonths = termMonths; }
    public String getPurpose() { return purpose; }
    public void setPurpose(String purpose) { this.purpose = purpose; }
}
