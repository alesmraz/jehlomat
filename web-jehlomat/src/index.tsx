import React, { lazy, Suspense } from 'react';
import ReactDOM from 'react-dom';
import { BrowserRouter as Router, HashRouter, Route, Switch } from 'react-router-dom';
import App from './App/App';
import Navigation from './Components/Navigation/Navigation';

/**
 * Lazy loading routes
 */
const Organizace = lazy(() => import("./Organizace/Organizace"));
const Profil = lazy(() => import("./Profil/Profil"));
const NovyNalez = lazy(() => import("./NovyNalez/NovyNalez"));
const Nalezy = lazy(() => import("./Nalezy/Nalezy"));
const Dekujeme = lazy(() => import("./Registrace/Dekujeme"));
const Registrace = lazy(() => import("./Registrace/Registrace"));
const OvereniEmailu = lazy(() => import("./RegistraceUzivatele/OvereniEmailu"));
const RegistraceUzivatel = lazy(() => import('./RegistraceUzivatele/RegistraceUzivatel'));
// **********************************************************************

/**
 *
 * TO-DO: Wrap whole application with AppContainer to simulate local state with logged user.
 * For future requests etc.
 */

ReactDOM.render(
    <React.StrictMode>
        <Suspense fallback={<div>Loading...</div>}>
            <HashRouter>
                {/*TODO: Navigation shouldn't be here, but on specific controllers*/}
                <Navigation />
                <Switch>
                    <Route path="/registraceUzivatele" component={RegistraceUzivatel}>
                        <RegistraceUzivatel />
                    </Route>
                    <Route
                        path="/registrace"
                        render={({ match: { url } }) => (
                            <>
                                <Route path={`${url}`} component={Registrace} exact />
                                <Route path={`${url}/dekujeme`} component={Dekujeme} />
                            </>
                        )}
                    />
                    <Route path="/profil" component={Profil}>
                        <Profil />
                    </Route>
                    <Route path="/organizace" component={Organizace}>
                        <Organizace />
                    </Route>
                    <Route path="/novy-nalez" component={NovyNalez}>
                        <NovyNalez />
                    </Route>
                    <Route path="/nalezy" component={Nalezy}>
                        <Nalezy />
                    </Route>
                    <Route path="/">
                        <App />
                    </Route>
                </Switch>
            </HashRouter>
        </Suspense>
    </React.StrictMode>,
    document.getElementById('root'),
);
