$(document).ready(function() {
    ////////////////////
    // Initialization //
    ////////////////////

    hideAllDetailItems();
    deactivateAllInScrollbar();

    ////////////////////
    // Helper methods //
    ////////////////////

    function hideAllDetailItems() {
        $(".detail-item").hide();
    }

    function deactivateAllInScrollbar() {
        $(".scroll-list > li").removeClass("active");
    }

    ////////////////////
    // Event handlers //
    ////////////////////

    $(".sidebar-item").on("click", function() {
        hideAllDetailItems();
        deactivateAllInScrollbar();
        var self = $(this),
            detailToShow = ".detail-" + self.attr("id");
        self.parent().addClass("active");
        $(detailToShow).show();
    });
});
