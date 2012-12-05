application {
    title = 'Merganser'
    startupGroups = ['merganser']

    // Should Griffon exit when no Griffon created frames are showing?
    autoShutdown = false

    // If you want some non-standard application class, apply it here
    //frameClass = 'javax.swing.JFrame'
}
mvcGroups {
    // MVC Group for "merganser"
    'merganser' {
        model      = 'merganser.MerganserModel'
        view       = 'merganser.MerganserView'
        controller = 'merganser.MerganserController'
    }

}
