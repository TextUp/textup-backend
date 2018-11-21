<g:applyLayout name="main">
    <html>
        <head>
            <title><g:layoutTitle default="TextUp API"/></title>
            <asset:stylesheet src="usage.css"/>
            <asset:stylesheet src="c3-0.6.8.css"/>
            <link rel="stylesheet" type="text/css" href="https://cdn.datatables.net/v/bs/dt-1.10.18/b-1.5.4/b-html5-1.5.4/datatables.min.css"/>
            <g:layoutHead/>
        </head>
        <body>
            <div class="container">
                <g:layoutBody/>
            </div>
            <content tag="assets">
                <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.1.36/pdfmake.min.js"></script>
                <script type="text/javascript" src="https://cdnjs.cloudflare.com/ajax/libs/pdfmake/0.1.36/vfs_fonts.js"></script>
                <script type="text/javascript" src="https://cdn.datatables.net/v/bs/dt-1.10.18/b-1.5.4/b-html5-1.5.4/datatables.min.js"></script>
            </content>
        </body>
    </html>
</g:applyLayout>
