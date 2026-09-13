(tag_start) @xml.tag
(tag_end) @xml.tag
(end_tag_element) @xml.tag
(empty_element) @xml.tag

(attribute) @attribute
(attr_value) @string

(comment) @comment
(cdata) @string
(char_ref) @xml.ref
(entity_ref) @xml.ref

(ns_decl) @ns_prefix

(xml_decl) @keyword
(xml_version_value) @string
(xml_encoding_value) @string
