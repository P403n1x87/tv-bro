console.log("Odysseus home content extension loaded");

const homeExtPort = browser.runtime.connectNative("tvbro");
function postMessageToHomePagePort(action, data) {
    //console.log("Sending message to native app: " + action);
    homeExtPort.postMessage({ action: action, data: data });
}

let Odysseus = {
    startVoiceSearch: function () {
        postMessageToHomePagePort("startVoiceSearch");
    },
    setSearchEngine: function (engine, customSearchEngineURL) {
        postMessageToHomePagePort("setSearchEngine", { engine: engine, customSearchEngineURL: customSearchEngineURL });
    },
    onEditBookmark: function (bookmark) {
        postMessageToHomePagePort("onEditBookmark", bookmark);
    },
    onHomePageLoaded: function () {
        postMessageToHomePagePort("onHomePageLoaded");
    },
    requestFavicon: function (url) {
        postMessageToHomePagePort("requestFavicon", url);
    },
    markBookmarkRecommendationAsUseful: function (bookmarkIndex) {
        postMessageToHomePagePort("markBookmarkRecommendationAsUseful", bookmarkIndex);
    }
}
window.wrappedJSObject.Odysseus = cloneInto(
    Odysseus,
    window,
    { cloneFunctions: true });
// Legacy alias: when the loopback asset server is down the home page falls back to the
// upstream-hosted copy, whose markup still looks up the old "TVBro" name.
window.wrappedJSObject.TVBro = window.wrappedJSObject.Odysseus;

homeExtPort.onMessage.addListener(message => {
    switch (message.action) {
        case "favicon": {
            let favicon = message.data;
            if (window.wrappedJSObject.onFaviconLoaded) {
                window.wrappedJSObject.onFaviconLoaded(favicon.url, favicon.data);
            }
        }
    }
});