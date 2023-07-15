"use strict";
!(function () {
  var dropzones = '<div class="dz-preview dz-file-preview">\n<div class="dz-details">\n  <div class="dz-thumbnail">\n    <img data-dz-thumbnail>\n    <span class="dz-nopreview">No preview</span>\n    <div class="dz-success-mark"></div>\n    <div class="dz-error-mark"></div>\n    <div class="dz-error-message"><span data-dz-errormessage></span></div>\n    <div class="progress">\n      <div class="progress-bar progress-bar-primary" role="progressbar" aria-valuemin="0" aria-valuemax="100" data-dz-uploadprogress></div>\n    </div>\n  </div>\n  <div class="dz-filename" data-dz-name></div>\n  <div class="dz-size" data-dz-size></div>\n</div>\n</div>';
  new Dropzone("#dropzone-basic", { previewTemplate: dropzones, parallelUploads: 1, maxFilesize: 5, addRemoveLinks: !0, maxFiles: 1 }), new Dropzone("#dropzone-multi", { previewTemplate: dropzones, parallelUploads: 1, maxFilesize: 5, addRemoveLinks: !0 });
})();
