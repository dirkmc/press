*{
 *  Outputs a <script> tag whose source is the compressed output of all the
 *  other javascript files referenced by #{press.script} tags.
 *
 *  eg:
 *  #{press.script src="widget.js"}
 *  #{press.script src="ui.js"}
 *  #{press.script src="validation.js"}
 *
 *  #{press.compressed-script}
 *
 *  See the plugin documentation for more information.
 *  
}*
${ press.Plugin.compressedJSTag() }