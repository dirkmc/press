*{
 *  Parameters
 *  - src (required) filename without the leading path "/public/stylesheets"
 *  - media (optional) media : screen, print, aural, projection ...
 *
 *  When the plugin is enabled, outputs nothing but adds the css file to the
 *  list of files to be compressed.
 *  When the plugin is disabled, outputs a css tag with the original source
 *  for easy debugging.
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
%{
    ( _arg ) &&  ( _src = _arg);

    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for stylesheet tag");
    }
}%
#{if press.Plugin.enabled() }
  %{ press.Plugin.addCSS(_src) }%
#{/if}
#{else}
  <link href="/public/stylesheets/${_src}" rel="stylesheet" type="text/css" charset="utf-8" #{if _media} media="${_media}"#{/if}></link>
#{/else}