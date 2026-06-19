<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="1.0" xmlns:xsl="http://www.w3.org/1999/XSL/Transform">
  <xsl:output method="xml" indent="yes" omit-xml-declaration="yes"/>
  <xsl:template match="/order">
    <invoice>
      <invoiceNumber><xsl:value-of select="concat('INV-', id)"/></invoiceNumber>
      <billTo><xsl:value-of select="customer"/></billTo>
      <amount><xsl:value-of select="amount"/></amount>
      <currency>USD</currency>
    </invoice>
  </xsl:template>
</xsl:stylesheet>
