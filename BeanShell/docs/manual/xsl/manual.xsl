<xsl:stylesheet	
	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0"
    xmlns:redirect="org.apache.xalan.xslt.extensions.Redirect"
    extension-element-prefixes="redirect"
>

<!--
	identity.xsl: Pass HTML (and other unknown markup) through.
	Note: this requires that later we match *all* of our own tags and
	explicitly remove them from output.  See match="section".
-->
<xsl:import href="identity.xsl"/>
<!-- Support for anchoring/linking text within document -->
<xsl:import href="anchor.xsl"/>
<!-- Handle example text -->
<xsl:import href="example.xsl"/>
<!-- Handle note text -->
<xsl:import href="note.xsl"/>
<!-- Handle tip text -->
<xsl:import href="tip.xsl"/>
<!-- Rewrite images using {$imagedir} -->
<xsl:import href="img.xsl"/>
<!-- Render indexed bsh commands doc -->
<xsl:import href="bshcommands.xsl"/>

<!-- Parameters -->
<xsl:param name="imagedir"/>
<xsl:param name="multipage"/><!-- Produce multi-page HTML output -->
<xsl:param name="pagesdir"/>

<!-- Output directives -->
<xsl:output method="xhtml" indent="yes"/>

<!-- 
	Root
	Override / in other imports, e.g. bshcommands.xsl 
-->
<xsl:template match="/">
	<!-- seems like there should be another way to do this using mode -->
	<xsl:choose>
	<xsl:when test="$multipage='true'">
		<xsl:apply-templates mode="multipage"/>
	</xsl:when>
	<xsl:otherwise>
		<xsl:apply-templates/>
	</xsl:otherwise>
	</xsl:choose>
</xsl:template>

<!-- 
	Manual
-->
<xsl:template match="manual">
	<html>
	<head><title>BeanShell User's Manual</title></head>
	<body bgcolor="#ffffff">
		<xsl:apply-templates/>
	</body>
	</html>
</xsl:template>

<xsl:template match="manual" mode="multipage">
	<xsl:apply-templates mode="multipage"/>
</xsl:template>

<!-- 
	Section
-->
<xsl:template match="section">
	<h1>
	<xsl:call-template name="anchor">
		<xsl:with-param name="value" select="name"/>
	</xsl:call-template>
	</h1>
	<!-- Just an experiment... alternative to declaring extra templates.
	<xsl:apply-templates 
		select="*[name()!='name' and name()!='foo']"/>
	-->
	<xsl:apply-templates/>
	<hr/>
	<xsl:comment>PAGE BREAK</xsl:comment>
</xsl:template>
<!-- remove control/meta-info tags -->
<xsl:template match="section/name"/>

<xsl:template match="section" mode="multipage">
	<!-- Check file name -->
	<xsl:if test="not(name[@filename])">
		<xsl:message terminate="yes">
			Missing filename attribute in section:
			<xsl:value-of select="name"/>
		</xsl:message>
	</xsl:if>
	<!-- Send output to file -->
    <redirect:write file="{$pagesdir}/{name/@filename}.html">
		<html>
		<head>
			<title><xsl:value-of select="name"/></title>
		</head>
		<body bgcolor="ffffff">
			<h1><xsl:value-of select="name"/></h1>
			<xsl:apply-templates/>
		</body>
		</html>
	</redirect:write>
</xsl:template>

<!--
	Table of contents
-->
<xsl:template match="tableofcontents">
	<ul>
	<xsl:for-each select="/manual/section">
		<li>
		<xsl:call-template name="anchorref">
			<xsl:with-param name="anchorto" select="name"/>
			<!-- how do I deal with the whitespace here? -->
			<xsl:with-param name="anchortofile"><xsl:if test="$multipage"><xsl:value-of select="name/@filename"/>.html</xsl:if></xsl:with-param>
			<xsl:with-param name="value" select="name"/>
		</xsl:call-template>
		</li>
		<xsl:if test="h2">
			<ul>
			<xsl:for-each select="h2">
				<li>
				<xsl:call-template name="anchorref">
					<xsl:with-param name="anchorto" select="."/>
					<!-- how do I deal with the whitespace here? -->
					<xsl:with-param name="anchortofile"><xsl:if test="$multipage"><xsl:value-of select="../name/@filename"/>.html</xsl:if></xsl:with-param>
					<xsl:with-param name="value" select="."/>
				</xsl:call-template>
				</li>
			</xsl:for-each>
			</ul>
		</xsl:if>
	</xsl:for-each>
	</ul>
	<hr/>
	<xsl:comment>PAGE BREAK</xsl:comment>
</xsl:template>

<!-- 
	Sub-Section
	Anchor each h2 sub-section 
-->
<xsl:template match="h2">
	<h2> 
	<xsl:call-template name="anchor">
		<xsl:with-param name="value" select="."/>
	</xsl:call-template>
	</h2>
</xsl:template>

<!-- 
	Rewrite <p/> tags to add a default clear attribute.  
	(Is there a simpler way to do this?)
	This is just here to help out htmldoc or other XHTML tools.
-->
<xsl:template match="p">
	<xsl:choose>
	<xsl:when test="@CLEAR">
		<xsl:copy-of select="."/>
	</xsl:when>
	<xsl:otherwise>
		<p CLEAR="ALL"/>
	</xsl:otherwise>
	</xsl:choose>
</xsl:template>

</xsl:stylesheet>

