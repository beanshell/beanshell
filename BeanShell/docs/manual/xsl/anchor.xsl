<xsl:stylesheet	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

<!-- 
	Anchor the specified text with an identifier formed by trimming space
	and translating spaces to underscores.
-->
<xsl:template name="anchor">
	<xsl:param name="value"/>
	<xsl:variable name="avalue" 
		select="translate(normalize-space($value), ' ', '_')"/>
	<a name="{$avalue}"><xsl:value-of select="$value"/></a>
</xsl:template>

<!-- 
	Link a reference to anchored text using the identifier scheme from
	the anchor template.
-->
<xsl:template name="anchorref">
	<xsl:param name="anchorto"/>
	<xsl:param name="anchortofile"/> <!-- default none -->
	<xsl:param name="value"/>
	<xsl:variable name="anchor" 
		select="translate(normalize-space($anchorto), ' ', '_')"/>
	<a href="{$anchortofile}#{$anchor}"><xsl:value-of select="$value"/></a>
</xsl:template>

</xsl:stylesheet>
