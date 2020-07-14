var getGraphDBAuthToken = function() {
    function getCookie(cname) {
        var name = cname + "=";
        var ca = document.cookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') c = c.substring(1);
            if (c.indexOf(name) == 0) return decodeURIComponent(c.substring(name.length, c.length));
        }
        return "";
    }

    var port = window.location.port;
    if (!port) {
        if (window.location.protocol == 'https:') {
            port = "443";
        }
        else {
            port = "80";
        }
    }

    return getCookie('com.ontotext.graphdb.auth' + port);
};

var setupGraphDBWorkbenchHeaders = function () {
    var headers = {};
    var graphDBAuth = getGraphDBAuthToken();
    if (graphDBAuth != '') {
        headers['Authorization'] = graphDBAuth;
    }
    $.ajaxSetup({ headers: headers });
};

setupGraphDBWorkbenchHeaders();
