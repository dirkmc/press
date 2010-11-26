*{
 *  Parameters:
 *  - src (required)        filename without the leading path eg "mystyles.css"
 *  - media (optional)      media : screen, print, aural, projection ...
 *  - compress (optional)   if set to false, file is added to compressed output,
 *                          but is not itself compressed
 *  - ignoreDuplicates (optional)
 *                          if set to true, the file can be included multiple
 *                          times and press will not throw an error. Only the
 *                          first occurrence of the file will be output.
 *
 *  When the plugin is enabled, outputs a comment and adds the css file to the
 *  list of files to be compressed.
 *  When the plugin is disabled, outputs a css tag with the original source
 *  for easy debugging.
 *
 *  eg:
 *  #{press.stylesheet src: "widget.css"}
 *  #{press.stylesheet src: "ui.css" compress:true}
 *  #{press.stylesheet src: "validation.css"}
 *
 *  #{press.compressed-stylesheet}
 *
 *  Source css files MUST be in utf-8 format.
 *  See the plugin documentation for more information.
 *  
}*
%{
    ( _arg ) &&  ( _src = _arg);
    
    // compress defaults to true
    if(_compress == null) {
      _compress = true;
    }
    
    // ignoreDuplicates defaults to false
    if(_ignoreDuplicates == null) {
      _ignoreDuplicates = false;
    }
    
    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for stylesheet tag");
    }

}%
#{if press.Plugin.performCompression() }
  ${ press.Plugin.addCSS(_src, _compress, _ignoreDuplicates) }
#{/if}
#{elseif press.Plugin.outputCSSTag(_src, _compress, _ignoreDuplicates) }
  <link href="/public/stylesheets/${_src}" rel="stylesheet" type="text/css" charset="utf-8" #{if _media} media="${_media}"#{/if}></link>
#{/elseif}