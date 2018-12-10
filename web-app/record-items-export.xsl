<?xml version="1.0" encoding="UTF-8"?>
<xsl:stylesheet version="3.0"
    xmlns:xsl="http://www.w3.org/1999/XSL/Transform"
    xmlns:fo="http://www.w3.org/1999/XSL/Format"
    xmlns:fox="http://xmlgraphics.apache.org/fop/extensions">

    <!-- Global context -->

    <xsl:param name="logoFilePath" />

    <xsl:variable name="totalNumItems" select="/map/entry[@key='totalNumItems']/number()" />
    <xsl:variable name="maxAllowedNumItems" select="/map/entry[@key='maxAllowedNumItems']/number()" />
    <xsl:variable name="exportedOnDate" select="/map/entry[@key='exportedOnDate']/text()" />
    <xsl:variable name="exportedBy" select="/map/entry[@key='exportedBy']/text()" />
    <xsl:variable name="phoneName" select="/map/entry[@key='phoneName']/text()" />
    <xsl:variable name="phoneNumber" select="/map/entry[@key='phoneNumber']/text()" />
    <xsl:variable name="startDate" select="/map/entry[@key='startDate']/text()" />
    <xsl:variable name="endDate" select="/map/entry[@key='endDate']/text()" />

    <!-- End global context -->

    <!-- Formatting -->

    <xsl:variable name="dateTimeFormat" select="'[MNn] [D], [Y0001]  [h1]:[m01] [P]'" />

    <!-- End formatting -->

    <!-- Styling -->

    <xsl:attribute-set name="text-small">
        <xsl:attribute name="font-size">9pt</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="text-light">
        <xsl:attribute name="color">#4c4c4c</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="space-top">
        <xsl:attribute name="space-before.minimum">10px</xsl:attribute>
        <xsl:attribute name="space-before.optimum">15px</xsl:attribute>
        <xsl:attribute name="space-before.maximum">18px</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="space-bottom--small">
        <xsl:attribute name="space-after.minimum">3px</xsl:attribute>
        <xsl:attribute name="space-after.optimum">5px</xsl:attribute>
        <xsl:attribute name="space-after.maximum">10px</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="boxed">
        <xsl:attribute name="border">0.5px solid #b2b2b2</xsl:attribute>
        <xsl:attribute name="fox:border-radius">1px</xsl:attribute>
        <xsl:attribute name="padding">0.3em</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="underline">
        <xsl:attribute name="text-decoration">underline</xsl:attribute>
    </xsl:attribute-set>

    <xsl:attribute-set name="bold">
        <xsl:attribute name="font-weight">700</xsl:attribute>
    </xsl:attribute-set>

    <!-- End styling -->

    <!-- Main template for transforming XML -> XSL FO -->
    <xsl:template match="/">
        <fo:root>
            <fo:layout-master-set>
              <fo:simple-page-master master-name="single-stream"
                page-width="8.5in"
                page-height="11in"
                margin-top="0.5in"
                margin-left="0.5in"
                margin-right="0.5in">
                <fo:region-body margin-top="1.1in"
                    margin-bottom="0.5in"/>
                <fo:region-before precedence="true"
                    extent="1in"/>
              </fo:simple-page-master>
            </fo:layout-master-set>
            <xsl:for-each select="map/entry[@key='sections']/map">
                <fo:page-sequence master-reference="single-stream">
                    <fo:static-content flow-name="xsl-region-before">
                        <xsl:call-template name="header" />
                    </fo:static-content>
                    <fo:flow flow-name="xsl-region-body">
                        <xsl:call-template name="overallInfo" />
                        <xsl:for-each select="entry[@key='recordItems']/map">
                            <fo:block keep-together.within-page="always"
                                xsl:use-attribute-sets="space-top">
                                <xsl:apply-templates select="."/>
                            </fo:block>
                        </xsl:for-each>
                        <xsl:if test="position() = count(../map)">
                            <fo:block id="lastBlock"></fo:block>
                        </xsl:if>
                    </fo:flow>
                </fo:page-sequence>
            </xsl:for-each>
        </fo:root>
    </xsl:template>

    <!-- Header for each page -->
    <xsl:template name="header">
        <fo:table table-layout="fixed" width="100%">
            <fo:table-body>
                <fo:table-row>
                    <fo:table-cell xsl:use-attribute-sets="text-small">
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">TextUp phone</fo:inline>:
                            <xsl:value-of select='$phoneName'/> (<xsl:value-of select='$phoneNumber'/>)
                        </fo:block>
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">Exported on</fo:inline>:
                            <xsl:value-of select='$exportedOnDate'/>
                        </fo:block>
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">Exported by</fo:inline>:
                            <xsl:value-of select='$exportedBy'/>
                        </fo:block>
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">Records from</fo:inline>:
                            <xsl:value-of select='$startDate'/> to <xsl:value-of select='$endDate'/>
                        </fo:block>
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">Records for</fo:inline>:
                            <xsl:choose>
                                <xsl:when test="count(entry/string) = 0">
                                    all records within <xsl:value-of select='$phoneName'/> (<xsl:value-of select='$phoneNumber'/>)
                                </xsl:when>
                                <xsl:when test="count(entry/string) = 1">
                                    <xsl:value-of select="entry/string/text()" />
                                    (<xsl:value-of select="position()" /> of <xsl:value-of select="count(../map)" />)
                                </xsl:when>
                                <xsl:otherwise>
                                    <xsl:value-of select="count(entry[@key='contactNames']/string)" /> contacts,
                                    <xsl:value-of select="count(entry[@key='sharedContactNames']/string)" /> shared contacts,
                                    <xsl:value-of select="count(entry[@key='tagNames']/string)" /> tags
                                </xsl:otherwise>
                            </xsl:choose>
                        </fo:block>
                        <xsl:if test="$totalNumItems > $maxAllowedNumItems">
                            <fo:block>
                                Exported <xsl:value-of select='$maxAllowedNumItems'/> (max allowed) of <xsl:value-of select='$totalNumItems'/> total items
                            </fo:block>
                        </xsl:if>
                    </fo:table-cell>
                    <fo:table-cell xsl:use-attribute-sets="text-small">
                        <fo:block text-align="right" xsl:use-attribute-sets="space-bottom--small">
                            <fo:external-graphic src="{$logoFilePath}"
                                height="0.5in"
                                content-height="scale-to-fit" />
                        </fo:block>
                        <fo:block text-align="right">
                            Page
                            <fo:page-number />
                            of
                            <fo:page-number-citation ref-id="lastBlock" />
                        </fo:block>
                    </fo:table-cell>
                </fo:table-row>
            </fo:table-body>
        </fo:table>
    </xsl:template>

    <!-- Overview statistics at the beginning of each section -->
    <xsl:template name="overallInfo">
        <xsl:if test="count(entry/string) > 0">
            <fo:block>
                This export is for
            </fo:block>
            <fo:list-block provisional-distance-between-starts="0.4cm"
                provisional-label-separation="0.15cm">
                <fo:list-item start-indent="0.5cm">
                    <fo:list-item-label end-indent="label-end()">
                        <fo:block>
                            <fo:inline>-</fo:inline>
                        </fo:block>
                    </fo:list-item-label>
                    <fo:list-item-body start-indent="body-start()">
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">
                                <xsl:value-of select="count(entry[@key='contactNames']/string)" />
                                contacts</fo:inline>:
                            <xsl:value-of select="string-join(entry[@key='contactNames']/string, ', ')" />
                        </fo:block>
                    </fo:list-item-body>
                </fo:list-item>
                <fo:list-item start-indent="0.5cm">
                    <fo:list-item-label end-indent="label-end()">
                        <fo:block>
                            <fo:inline>-</fo:inline>
                        </fo:block>
                    </fo:list-item-label>
                    <fo:list-item-body start-indent="body-start()">
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">
                                <xsl:value-of select="count(entry[@key='sharedContactNames']/string)" />
                                shared contacts</fo:inline>:
                            <xsl:value-of select="string-join(entry[@key='sharedContactNames']/string, ', ')" />
                        </fo:block>
                    </fo:list-item-body>
                </fo:list-item>
                <fo:list-item start-indent="0.5cm">
                    <fo:list-item-label end-indent="label-end()">
                        <fo:block>
                            <fo:inline>-</fo:inline>
                        </fo:block>
                    </fo:list-item-label>
                    <fo:list-item-body start-indent="body-start()">
                        <fo:block>
                            <fo:inline xsl:use-attribute-sets="underline">
                                <xsl:value-of select="count(entry[@key='tagNames']/string)" />
                                tags</fo:inline>:
                            <xsl:value-of select="string-join(entry[@key='tagNames']/string, ', ')" />
                        </fo:block>
                    </fo:list-item-body>
                </fo:list-item>
            </fo:list-block>
        </xsl:if>
        <fo:block xsl:use-attribute-sets="space-top">
            This export contains <xsl:value-of select="count(entry[@key='recordItems']/map/entry[@key='type'])" /> record items
        </fo:block>
        <fo:list-block provisional-distance-between-starts="0.4cm"
            provisional-label-separation="0.15cm">
            <fo:list-item start-indent="0.5cm">
                <fo:list-item-label end-indent="label-end()">
                    <fo:block>
                        <fo:inline>-</fo:inline>
                    </fo:block>
                </fo:list-item-label>
                <fo:list-item-body start-indent="body-start()">
                    <fo:block>
                        <xsl:value-of select="count(entry[@key='recordItems']/map/entry[@key='type' and text()='TEXT'])" />
                        texts
                    </fo:block>
                </fo:list-item-body>
            </fo:list-item>
            <fo:list-item start-indent="0.5cm">
                <fo:list-item-label end-indent="label-end()">
                    <fo:block>
                        <fo:inline>-</fo:inline>
                    </fo:block>
                </fo:list-item-label>
                <fo:list-item-body start-indent="body-start()">
                    <fo:block>
                        <xsl:value-of select="count(entry[@key='recordItems']/map/entry[@key='type' and text()='CALL'])" />
                        calls
                    </fo:block>
                </fo:list-item-body>
            </fo:list-item>
            <fo:list-item start-indent="0.5cm">
                <fo:list-item-label end-indent="label-end()">
                    <fo:block>
                        <fo:inline>-</fo:inline>
                    </fo:block>
                </fo:list-item-label>
                <fo:list-item-body start-indent="body-start()">
                    <fo:block>
                        <xsl:value-of select="count(entry[@key='recordItems']/map/entry[@key='type' and text()='NOTE'])" />
                        notes
                    </fo:block>
                </fo:list-item-body>
            </fo:list-item>
        </fo:list-block>
    </xsl:template>

    <!-- Single record item -->
    <xsl:template match="map">
        <fo:block text-align-last="justify"
            xsl:use-attribute-sets="boxed space-bottom--small text-small">
            <xsl:choose>
                <xsl:when test="entry[@key='outgoing' and text()='true']">
                    Outgoing
                </xsl:when>
                <xsl:when test="entry[@key='outgoing' and text()='false']">
                    Incoming
                </xsl:when>
            </xsl:choose>
            <xsl:value-of select="lower-case(entry[@key='type'])" />

            <fo:leader leader-pattern="space" />

            <xsl:value-of select="format-dateTime(entry[@key='whenCreated'], $dateTimeFormat)" />
        </fo:block>
        <fo:block xsl:use-attribute-sets="space-bottom--small">
            <xsl:choose>
                <xsl:when test="entry[@key='outgoing' and text()='true']">
                    <fo:block>
                        From
                        <xsl:choose>
                            <xsl:when test="entry[@key='authorName']">
                                <fo:inline xsl:use-attribute-sets="bold">
                                    <xsl:value-of select="entry[@key='authorName']" />
                                </fo:inline>
                            </xsl:when>
                            <xsl:otherwise>
                                <fo:inline xsl:use-attribute-sets="text-light">
                                    not recorded
                                </fo:inline>
                            </xsl:otherwise>
                        </xsl:choose>
                    </fo:block>
                    <fo:block>
                        To
                        <fo:inline xsl:use-attribute-sets="bold">
                            <xsl:value-of select="entry[@key='ownerName']" />
                        </fo:inline>
                        <xsl:apply-templates select="entry[@key='receipts']" />
                    </fo:block>
                </xsl:when>
                <xsl:otherwise>
                    <fo:block>
                        From
                        <fo:inline xsl:use-attribute-sets="bold">
                            <xsl:value-of select="entry[@key='ownerName']" />
                        </fo:inline>
                    </fo:block>
                    <fo:block>
                        To
                        <fo:inline xsl:use-attribute-sets="bold">
                            <xsl:value-of select="../../entry[@key='phoneName']/text()" />
                            (<xsl:value-of select="../../entry[@key='phoneNumber']/text()" />)
                        </fo:inline>
                    </fo:block>
                </xsl:otherwise>
            </xsl:choose>
        </fo:block>
        <xsl:if test="entry[@key='durationInSeconds'] | entry[@key='contents'] | entry[@key='noteContents'] | entry[@key='media']/*">
            <fo:list-block provisional-distance-between-starts="0.4cm"
                provisional-label-separation="0.15cm">
                <xsl:if test="entry[@key='durationInSeconds']">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                <xsl:choose>
                                    <xsl:when test="entry[@key='voicemailInSeconds' and text()!='0']">
                                        <xsl:value-of select="entry[@key='voicemailInSeconds']" />
                                        second voicemail message
                                    </xsl:when>
                                    <xsl:otherwise>
                                        <xsl:value-of select="entry[@key='durationInSeconds']" />
                                        second phone call
                                    </xsl:otherwise>
                                </xsl:choose>
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="entry[@key='contents']">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                <fo:inline xsl:use-attribute-sets="underline">Contents</fo:inline>:
                                <xsl:value-of select="entry[@key='contents']" />
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="entry[@key='noteContents']">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                <fo:inline xsl:use-attribute-sets="underline">Note</fo:inline>:
                                <xsl:value-of select="entry[@key='noteContents']" />
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="entry[@key='media']/*">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                <fo:inline xsl:use-attribute-sets="underline">Attachments</fo:inline>:
                                <xsl:apply-templates select="entry[@key='media']" />
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="entry[@key='location']/entry[@key='address']">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                <fo:inline xsl:use-attribute-sets="underline">Location</fo:inline>:
                                <xsl:apply-templates select="entry[@key='location']/entry[@key='address']" />
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
            </fo:list-block>
        </xsl:if>
    </xsl:template>

    <!-- Receipts within a single record item -->
    <xsl:template match="entry[@key='receipts']">
        <xsl:if test="count(*/string)">
            <fo:list-block provisional-distance-between-starts="0.4cm"
                provisional-label-separation="0.15cm">
                <xsl:if test="count(entry[@key='success']/string)">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                Successfully received by
                                <xsl:value-of select="count(entry[@key='success']/string)" />
                                numbers:
                                <fo:inline xsl:use-attribute-sets="text-light">
                                    <xsl:value-of select="string-join(entry[@key='success']/string, ', ')" />
                                </fo:inline>
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="count(entry[@key='pending']/string)">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                Pending for
                                <xsl:value-of select="count(entry[@key='pending']/string)" />
                                numbers:
                                <fo:inline xsl:use-attribute-sets="text-light">
                                    <xsl:value-of select="string-join(entry[@key='pending']/string, ', ')" />
                                </fo:inline>
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="count(entry[@key='busy']/string)">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                Busy for
                                <xsl:value-of select="count(entry[@key='busy']/string)" />
                                numbers:
                                <fo:inline xsl:use-attribute-sets="text-light">
                                    <xsl:value-of select="string-join(entry[@key='busy']/string, ', ')" />
                                </fo:inline>
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
                <xsl:if test="count(entry[@key='failed']/string)">
                    <fo:list-item start-indent="0.5cm">
                        <fo:list-item-label end-indent="label-end()">
                            <fo:block>
                                <fo:inline>-</fo:inline>
                            </fo:block>
                        </fo:list-item-label>
                        <fo:list-item-body start-indent="body-start()">
                            <fo:block>
                                Failed for
                                <xsl:value-of select="count(entry[@key='failed']/string)" />
                                numbers:
                                <fo:inline xsl:use-attribute-sets="text-light">
                                    <xsl:value-of select="string-join(entry[@key='failed']/string, ', ')" />
                                </fo:inline>
                            </fo:block>
                        </fo:list-item-body>
                    </fo:list-item>
                </xsl:if>
            </fo:list-block>
        </xsl:if>
    </xsl:template>

    <!-- Media for a single record item -->
    <xsl:template match="entry[@key='media']">
        <xsl:value-of select="count(entry[@key='audio']/map)" /> audio recordings,
        <xsl:value-of select="count(entry[@key='images']/map)" /> images
    </xsl:template>
</xsl:stylesheet>
