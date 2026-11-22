//download blobs support
if (!window.odysseusClicksListener) {
    window.odysseusClicksListener = function(e) {
        if (e.target.tagName.toUpperCase() == "A" && e.target.attributes.href.value.toLowerCase().startsWith("blob:")) {
            var fileName = e.target.download;
            var url = e.target.attributes.href.value;
            var xhr=new XMLHttpRequest();
            xhr.open('GET', e.target.attributes.href.value, true);
            xhr.responseType = 'blob';
            xhr.onload = function(e) {
                if (this.status == 200) {
                    var blob = this.response;
                    var reader = new FileReader();
                    reader.readAsDataURL(blob);
                    reader.onloadend = function() {
                        base64data = reader.result;
                        Odysseus.takeBlobDownloadData(base64data, fileName, url, blob.type);
                    }
                }
            };
            xhr.send();
            e.stopPropagation();
            e.preventDefault();
        }
    };
    document.addEventListener("click", window.odysseusClicksListener);
}

// Stub for blocked ad/pop libraries
if (typeof window.aclib === 'undefined') {
    window.aclib = {
        runPop: function() {}
    };
}

// video playback control support
Object.defineProperty(HTMLMediaElement.prototype, 'playing', {
    get: function(){
        return !!(this.currentTime > 0 && !this.paused && !this.ended && this.readyState > 2);
    }
})

window.odysseusTogglePlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      if (media.playing) {
        media.pause();
      } else {
        media.play();
      }
  }
}

window.odysseusStopPlayback = function() {
  var media = document.querySelector('video') || document.querySelector('audio');
  if (media) {
      media.pause();
      media.currentTime = 0;
  }
}

window.odysseusRewind = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime -= 10;
    }
}

window.odysseusFastForward = function() {
    var media = document.querySelector('video') || document.querySelector('audio');
    if (media) {
        media.currentTime += 10;
    }
}

// context menu support
window.addEventListener("touchstart", function(e) {
    window.ODYSSEUS_activeElement = e.target;
    window.ODYSSEUS_touchStartX = e.touches[0].clientX;
    window.ODYSSEUS_touchStartY = e.touches[0].clientY;
});

// magnet link detection and conversion
if (!window.odysseusMagnetLinksProcessed) {
    window.odysseusMagnetLinksProcessed = true;
    
    const magnetLinkRegex = /magnet:\?xt=urn:btih:[a-zA-Z0-9]{32,40}(?:&[^"\s<>]*)?/gi;
    
    function processTextNode(textNode) {
        const text = textNode.textContent;
        const matches = [...text.matchAll(magnetLinkRegex)];
        if (matches.length === 0) return;
        
        const parent = textNode.parentNode;
        if (!parent) return;
        
        let lastIndex = 0;
        const fragment = document.createDocumentFragment();
        
        for (const match of matches) {
            const beforeText = text.substring(lastIndex, match.index);
            if (beforeText) {
                fragment.appendChild(document.createTextNode(beforeText));
            }
            
            const link = document.createElement('a');
            link.href = match[0];
            link.textContent = match[0];
            link.style.textDecoration = 'underline';
            link.style.color = '#1a73e8';
            link.style.cursor = 'pointer';
            fragment.appendChild(link);
            
            lastIndex = match.index + match[0].length;
        }
        
        const afterText = text.substring(lastIndex);
        if (afterText) {
            fragment.appendChild(document.createTextNode(afterText));
        }
        
        parent.replaceChild(fragment, textNode);
    }
    
    function scanForMagnetLinks(node) {
        if (node.nodeType === Node.TEXT_NODE) {
            processTextNode(node);
        } else if (node.nodeType === Node.ELEMENT_NODE) {
            // Skip script, style, and already processed anchor tags
            const tagName = node.tagName.toLowerCase();
            if (tagName !== 'script' && tagName !== 'style' && tagName !== 'a') {
                for (let i = 0; i < node.childNodes.length; i++) {
                    scanForMagnetLinks(node.childNodes[i]);
                }
            }
        }
    }
    
    // Run on DOMContentLoaded and also immediately in case it's already loaded
    function runMagnetLinkDetection() {
        scanForMagnetLinks(document.body);
    }
    
    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', runMagnetLinkDetection);
    } else {
        runMagnetLinkDetection();
    }
    
    // Also observe for dynamically added content
    const observer = new MutationObserver(function(mutations) {
        for (const mutation of mutations) {
            for (const node of mutation.addedNodes) {
                scanForMagnetLinks(node);
            }
        }
    });
    observer.observe(document.body, { childList: true, subtree: true });
}