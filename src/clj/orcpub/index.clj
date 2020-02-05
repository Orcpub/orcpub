(ns orcpub.index
  (:require [hiccup.page :refer [html5 include-css include-js]]
            [orcpub.oauth :as oauth]
            [orcpub.dnd.e5.views-2 :as views-2]))

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
   {:lang :en
    :style "height:100%"}
   [:head
    (meta-tag "og:url" url)
    (meta-tag "og:type" fb-type)
    (meta-tag "og:title" title)
    (meta-tag "og:description" description)
    (meta-tag "og:image" image)
    (meta-tag "google-signin-client_id" "86323071944-te5j96nbke0duomgm24j2on4rs4p7ob9.apps.googleusercontent.com")
    [:meta {:charset "UTF-8"}]
    [:meta {:name "viewport"
            :content "width=device-width, initial-scale=1"}]
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

    #app {height:100%;background-image: linear-gradient(182deg, #313A4D, #080A0D)}

    .app {background-image: linear-gradient(182deg, #313A4D, #080A0D);height:100%;overflow-y:scroll;-webkit-overflow-scrolling :touch;font-family:Open Sans, sans-serif}

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

    html, body, #app {
        height: 100%;
    }"
    ];end [style
    [:title title]
;;TODO FB removal ;TK
;    [:script
;     (format
;      "   window.fbAsyncInit = function() {;
;	    FB.init({
;	    appId      : '%s',
;  	  xfbml      : true,
;            cookie     : true,
;  	  version    : 'v2.9'
;  	  });
;  	  FB.AppEvents.logPageView();
;  	  };
;
;  	  (function(d, s, id){
;  	  var js, fjs = d.getElementsByTagName(s)[0];
;  	  if (d.getElementById(id)) {return;}
;  	  js = d.createElement(s); js.id = id;
;  	  js.src = \"//connect.facebook.net/en_US/sdk.js\";
;  	  fjs.parentNode.insertBefore(js, fjs);
;  	  }(document, 'script', 'facebook-jssdk'));"
;      (oauth/app-id url)
;    )]
   ] ;;END [head
   [:body {:style "margin:0;height:100%;line-height:1"}
    [:div#app
     (if splash?
       (views-2/splash-page)
       [:div {:style "display:flex;justify-content:space-around"}
        [:img {:src "/image/spiral.gif"
               :style "height:200px;width:200px;margin-top:200px"}]])]
    (include-css "/css/compiled/styles.css")
    (include-js "/js/compiled/orcpub.js")
    (include-css "/font-awesome-4.7.0/css/font-awesome.min.css")
    (include-css "https://fonts.googleapis.com/css?family=Open+Sans")
    [:script
     "(function(i,s,o,g,r,a,m){i['GoogleAnalyticsObject']=r;i[r]=i[r]||function(){
	  (i[r].q=i[r].q||[]).push(arguments)},i[r].l=1*new Date();a=s.createElement(o),
	  m=s.getElementsByTagName(o)[0];a.async=1;a.src=g;m.parentNode.insertBefore(a,m)
	  })(window,document,'script','https://www.google-analytics.com/analytics.js','ga');

	  ga('create', 'UA-69209720-3', 'auto');
    ga('send', 'pageview');
    let plugins = localStorage.getItem('plugins');
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
