<xsl:stylesheet	xmlns:xsl="http://www.w3.org/1999/XSL/Transform" version="1.0">

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
<!-- Rewrite images using {$imagedir} -->
<xsl:import href="img.xsl"/>
<!-- Render indexed bsh commands doc -->
<xsl:import href="bshcommands.xsl"/>

<!-- Parameters -->
<xsl:param name="multipage"/>
<xsl:param name="imagedir"/>

<!-- Output directives -->
<xsl:output method="xhtml" indent="yes"/>

<!-- 
	Root
	Override / in other imports, e.g. bshcommands.xsl 
-->
<xsl:template match="/">
	<xsl:apply-templates/>
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
	Table of contents
-->
<xsl:template match="/manual/contents">
	<h1>Table of Contents</h1>
	<ul>
	<xsl:for-each select="/manual/section">
		<li>
		<xsl:call-template name="anchorref">
			<xsl:with-param name="anchorto" select="name"/>
			<xsl:with-param name="value" select="name"/>
		</xsl:call-template>
		</li>
		<xsl:if test="h2">
			<ul>
			<xsl:for-each select="h2">
				<li>
				<xsl:call-template name="anchorref">
					<xsl:with-param name="anchorto" select="."/>
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


</xsl:stylesheet>

