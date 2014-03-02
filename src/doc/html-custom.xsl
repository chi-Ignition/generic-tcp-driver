 <xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xslthl="http://xslthl.sf.net"
    xmlns:d="http://docbook.org/ns/docbook"
    exclude-result-prefixes="xslthl"
    version="1.0">

	<xsl:import href="urn:docbkx:stylesheet/profile-chunk.xsl"/>
  
	<xsl:param name="target.window" select="'body'"/>
	<xsl:template name="user.head.content">
		<base  target="{$target.window}"/>
	</xsl:template>
  
</xsl:stylesheet>