*{
 *  Parameters:
 *  - src (required)       filename without the leading path eg "myscript.js"
 *  - media (optional)     media : screen, print, aural, projection ...
 *  - compress (optional)  if set to false, file is added to compressed output,
 *                         but is not itself compressed
 *
 *  When the plugin is enabled, outputs a comment and adds the script to the
 *  list of files to be compressed.
 *  When the plugin is disabled, outputs a script tag with the original source
 *  for easy debugging.
 *
 *  eg:
 *  #{press.script src: "jquery.min.js", compress: false}
 *  #{press.script src: "widget.js"}
 *  #{press.script src: "ui.js"}
 *  #{press.script src: "validation.js"}
 *  #{press.script src: "path/*.js"} <!-- include all JS from "path" -->
 *  #{press.script src: "path/**.js"} <!-- recursively include all JS from "path" -->
 *
 *  #{press.compressed-script}
 *
 *  Source script files MUST be in utf-8 format.
 *  See the plugin documentation for more information.
 *  
}*
%{
    ( _arg ) &&  ( _src = _arg);
    
    // compress defaults to true
    if(_compress == null) {
      _compress = true;
    }
    
    if(! _src) {
        throw new play.exceptions.TagInternalException("src attribute cannot be empty for press.script tag");
    }

}%
${ press.Plugin.addJS(_src, _compress) }