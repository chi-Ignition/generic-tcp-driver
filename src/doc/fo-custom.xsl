 <xsl:stylesheet 
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:xslthl="http://xslthl.sf.net"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    exclude-result-prefixes="xslthl"
    version="1.0">

	<xsl:import href="urn:docbkx:stylesheet"/>

	<xsl:param name="monospace.font.family">monospace</xsl:param>

	<xsl:attribute-set name="section.level1.properties">
	        <xsl:attribute name="break-before">page</xsl:attribute>
	</xsl:attribute-set>

    <xsl:attribute-set name="normal.para.spacing">
		<xsl:attribute name="space-before.optimum">
		 	.5em
		</xsl:attribute>
		<xsl:attribute name="space-before.minimum">
			.3em
	    </xsl:attribute>
		<xsl:attribute name="space-before.maximum">
			.8em
		</xsl:attribute>
    </xsl:attribute-set>

	<xsl:attribute-set name="monospace.verbatim.properties">
	  <xsl:attribute name="font-family">Lucida Sans Typewriter</xsl:attribute>
	  <xsl:attribute name="font-size">9pt</xsl:attribute>
	  <xsl:attribute name="keep-together.within-column">always</xsl:attribute>
	</xsl:attribute-set>
	
	<xsl:param name="shade.verbatim" select="1"/>
	<xsl:attribute-set name="shade.verbatim.style">
		<xsl:attribute name="border">thin black solid</xsl:attribute>
		<xsl:attribute name="background-color">#E0E0E0</xsl:attribute>
	</xsl:attribute-set>

	<xsl:attribute-set name="variablelist.term.properties">
		<xsl:attribute  name="font-weight">bold</xsl:attribute>
	</xsl:attribute-set>
  
	<xsl:attribute-set name="xref.properties">
	  <xsl:attribute name="color">blue</xsl:attribute>
	  <xsl:attribute name="text-decoration">underline</xsl:attribute>
	</xsl:attribute-set>
  
</xsl:stylesheet>
