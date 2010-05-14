*{
 *  When the plugin is enabled, outputs nothing but adds the script to the
 *  list of files to be compressed.
 *  When the plugin is disabled, outputs a script tag with the original source
 *  for easy debugging.
 *
 *  eg:
 *  #{press.script src="widget.js"}
 *  #{press.script src="ui.js"}
 *  #{press.script src="validation.js"}
 *
 *  #{press.compressed-script}
 *
 *  Source script files MUST be in utf-8 format.
 *  See the plugin documentation for more information.
 *  
}*
%{
    ( _arg ) &&  ( _src = _arg);

    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for press.script tag");
    }
}%
#{if press.Plugin.enabled() }
  %{ press.Plugin.addJS(_src) }%
#{/if}
#{else}
  <script src="/public/javascripts/${_src}" type="text/javascript" language="javascript" charset="utf-8"></script>
#{/else}