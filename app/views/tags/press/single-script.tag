*{
 *  Outputs a <script> tag whose source is the compressed output of the file
 *  specified as a parameter
 *  When the plugin is disabled, outputs a script tag with the original source
 *  for easy debugging.
 *
 *  eg:
 *  #{press.single-script "widget.js"}
 *
 *  will output:
 *  <script src="/public/javascripts/press/widget.min.js" type="text/javascript" language="javascript" charset="utf-8"/>
 *
 *  See the plugin documentation for more information.
 *  
}*
%{
    ( _arg ) &&  ( _src = _arg);
    
    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for press.single-script tag");
    }
}%

#{if press.Plugin.enabled() && !press.Plugin.hasErrorOccurred() }
  <script src="${press.Plugin.compressedSingleJSUrl(_src)}" type="text/javascript" language="javascript" charset="utf-8"></script>
#{/if}
#{else}
  %{ press.Plugin.checkJSFileExists(_src) }%
  <script src="/public/javascripts/${_src}" type="text/javascript" language="javascript" charset="utf-8"></script>
#{/else}