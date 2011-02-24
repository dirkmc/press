*{
 *  Parameters
 *  - media (optional) media : screen, print, aural, projection ...
 *
 *  Outputs a <link rel="stylesheet"> tag that links to the compressed output
 *  of all the other stylesheet files referenced by #{press.stylesheet} tags.
 *
 *  eg:
 *  #{press.stylesheet src="widget.css"}
 *  #{press.stylesheet src="ui.css"}
 *  #{press.stylesheet src="validation.css"}
 *
 *  #{press.compressed-stylesheet}
 *
 *  Source css files MUST be in utf-8 format.
 *  See the plugin documentation for more information.
 *  
}*
${ press.Plugin.compressedCSSTag() }