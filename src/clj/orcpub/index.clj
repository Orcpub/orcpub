(ns orcpub.index
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [orcpub.oauth :as oauth]
            [orcpub.dnd.e5.views-2 :as views-2]
            [orcpub.favicon :as fi]))

(defn meta-tag [property content]
  (if content
    [:meta
     {:property property
      :content content}]))

(defn index-page [{:keys [url
                          title
                          description
                          image
                          fb-type]}
                  & [splash?]]
  (html5
   {:lang :en}
   [:head
    (meta-tag "og:url" url)
    (meta-tag "og:type" fb-type)
    (meta-tag "og:title" title)
    (meta-tag "og:description" description)
    (meta-tag "og:image" image)
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1.0, minimum-scale=1.0"}]
    (fi/install :png-prefix "favicon-"
                :img "/favicon"
                :xml "/favicon"
                :ver "1")
    (include-css "/css/cookiestyles.css")
    [:script
     "document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`);
     window.addEventListener('resize', () => document.documentElement.style.setProperty('--innerHeight', `${window.innerHeight}px`));"]
    [:style
     "
.splash-page-content {}
.splash-button .splash-button-content {height: 120px; width: 120px}
.splash-button .svg-icon {height: 64px; width: 64px}

@media (max-width: 767px) 
{.splash-button .svg-icon {height: 32px; width: 32px}
.splash-button-title-prefix {display: none}
.splash-button .splash-button-content {height: 60px; width: 60px; font-size: 10px}
.legal-footer-parent {display: none}}

body {background-color: #080A0D}

#app {background-image: linear-gradient(182deg, #313A4D, #080A0D);background-attachment: fixed}

.app {height:100%;font-family:Open Sans, sans-serif}

.h-full {height: 100vh;height: var(--innerHeight, 100vh)}

.min-h-full {min-height: 100vh;min-height: var(--innerHeight, 100vh)}

html, body, div, span, applet, object, iframe,
h1, h2, h3, h4, h5, h6, p, blockquote, pre,
a, abbr, acronym, address, big, cite, code,
del, dfn, em, img, ins, kbd, q, s, samp,
small, strike, strong, sub, sup, tt, var,
b, u, i, center,
dl, dt, dd, ol, ul, li,
fieldset, form, label, legend,
table, caption, tbody, tfoot, thead, tr, th, td,
article, aside, canvas, details, figcaption, figure, 
footer, header, hgroup, menu, nav, section, summary,
time, mark, audio, video {
	margin: 0;
	padding: 0;
	border: 0;
	outline: 0;
	font-size: 100%;
	font: inherit;
	vertical-align: baseline;
}
/* HTML5 display-role reset for older browsers */
article, aside, details, figcaption, figure, 
footer, header, hgroup, menu, nav, section {
	display: block;
}
body {
	line-height: 1;
}
ol, ul {
	list-style: none;
}
blockquote, q {
	quotes: none;
}
blockquote:before, blockquote:after,
q:before, q:after {
	content: '';
	content: none;
}
ins {
	text-decoration: none;
}
del {
	text-decoration: line-through;
}

table {
	border-collapse: collapse;
	border-spacing: 0;
}

html {
	min-height: 100%;
}"]
    [:title title]]
   [:body {:style "margin:0;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       [:div.h-full {:style "display:flex;justify-content:space-around"}
        [:img {:src "/image/spiral.gif"
               :style "height:200px;width:200px;margin-top:200px"}]])]
    (include-css "/css/compiled/styles.css")
    (include-js "/js/compiled/orcpub.js")
    (include-js "/js/cookies.js")
    (include-css "/font-awesome-5.14.0/css/all.css")
    (include-css "https://fonts.googleapis.com/css?family=Open+Sans")
    [:script " window.start.init({Palette:\"palette7\",Mode:\"banner bottom\",})"]    
    [:script
     "let plugins = localStorage.getItem ('plugins');
     if(plugins === null || plugins === '{}')
     {
       fetch('https://' + window.location.host + '/homebrew.orcbrew')
         .then(resp => resp.text())
         .then(text => {
           if(!text.toUpperCase().includes('NOT FOUND')){
             localStorage.setItem('plugins',text);
             window.location.reload(false);
           }
       });
     }
    "]]))
