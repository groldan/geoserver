<?xml version="1.0" encoding="ISO-8859-1"?>
<StyledLayerDescriptor version="1.0.0"
    xsi:schemaLocation="http://www.opengis.net/sld StyledLayerDescriptor.xsd"
    xmlns="http://www.opengis.net/sld"
    xmlns:ogc="http://www.opengis.net/ogc"
    xmlns:xlink="http://www.w3.org/1999/xlink"
    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
  <NamedLayer>
    <Name>Line with border</Name>
    <UserStyle>
    <Title>SLD Cook Book: Line w2th border</Title>
      <FeatureTypeStyle>
         <Rule>
          <LineSymbolizer>
            <Stroke>
                <CssParameter name="stroke-opacity">0.5</CssParameter>
              <CssParameter name="stroke">#333333</CssParameter>
              <CssParameter name="stroke-width">3.0</CssParameter>
              <CssParameter name="stroke-linecap">round</CssParameter>
              <CssParameter name="stroke-dasharray">5 2</CssParameter>
            </Stroke>
          </LineSymbolizer>
          <LineSymbolizer>
              <Stroke>
                   <GraphicStroke>
                         <Graphic>
                           <Mark>
                             <WellKnownName>shape://vertline</WellKnownName>
                             <Stroke>
                               <CssParameter name="stroke">#333333</CssParameter>
                             </Stroke>
                           </Mark>
                           <Size>12</Size>
                         </Graphic>
                       </GraphicStroke>
               </Stroke>
          </LineSymbolizer>
        </Rule>
      </FeatureTypeStyle>
    </UserStyle>
  </NamedLayer>
</StyledLayerDescriptor>
