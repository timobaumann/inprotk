<?xml version="1.0" encoding="UTF-8"?>
<!-- used internally by TTSUtil when converting MaryXML to IU representation --> 
<xsl:stylesheet 
  version="1.0" 
  xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
  xmlns:mary="http://mary.dfki.de/2002/MaryXML"
>
<xsl:output method="xml" indent="no"/>

<xsl:template match="/"> <!-- root element is replaced against <all> -->
 <xsl:element name="all"><xsl:apply-templates /></xsl:element>
</xsl:template>

<xsl:template match="mary:phrase">
 <xsl:element name="phr">
  <xsl:attribute name="tone"><xsl:value-of select="mary:boundary/attribute::tone" /></xsl:attribute>
  <xsl:attribute name="breakIndex"><xsl:value-of select="mary:boundary/attribute::breakindex" /></xsl:attribute>
  <xsl:attribute name="pitchOffset"><xsl:value-of select="ancestor::mary:prosody/attribute::pitch" /></xsl:attribute>
  <xsl:attribute name="pitchRange"><xsl:value-of select="ancestor::mary:prosody/attribute::range" /></xsl:attribute>
  <xsl:apply-templates />
 </xsl:element>
</xsl:template>

<xsl:template match="mary:t">
 <xsl:element name="t">
  <xsl:attribute name="pos"><xsl:value-of select="@pos" /></xsl:attribute>
  <xsl:apply-templates />
 </xsl:element>
</xsl:template>

<xsl:template match="mary:syllable">
 <xsl:element name="syl">
  <xsl:attribute name="accent"><xsl:value-of select="@accent" /></xsl:attribute>
  <xsl:attribute name="stress"><xsl:value-of select="@stress" /></xsl:attribute>
  <xsl:apply-templates />
 </xsl:element>
</xsl:template>

<xsl:template match="mary:boundary">
 <xsl:element name="t">
  <xsl:attribute name="isBreak"><xsl:text>true</xsl:text></xsl:attribute>
   <xsl:copy-of select="(preceding-sibling::mary:t)[last()]/node()" />
  <xsl:element name="syl">
   <xsl:element name="seg">
    <xsl:attribute name="d"><xsl:apply-templates select="@duration" /></xsl:attribute>
    <xsl:attribute name="p"><xsl:text>_</xsl:text></xsl:attribute>
    <xsl:attribute name="end"><xsl:value-of select="preceding::mary:ph[1]/attribute::end + @duration * 0.001" /></xsl:attribute>
   </xsl:element>	
  </xsl:element>
 </xsl:element>
</xsl:template>

<xsl:template match="mary:ph">
 <xsl:element name="seg">
    <!-- the rounding is necessary because Mary sometimes outputs doubles instead of ints for duration (e.g. "54.0" instead of "54") -->
    <xsl:attribute name="d"><xsl:value-of select="round(number(@d))" /></xsl:attribute>
  <xsl:attribute name="end"><xsl:apply-templates select="@end" /></xsl:attribute>
  <xsl:attribute name="f0"><xsl:apply-templates select="@f0" /></xsl:attribute>
  <xsl:attribute name="p"><xsl:apply-templates select="@p" /></xsl:attribute>
  <!--xsl:apply-templates select="node()|@*" /-->
 </xsl:element>
</xsl:template>
</xsl:stylesheet>
