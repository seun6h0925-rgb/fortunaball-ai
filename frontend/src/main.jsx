import { StrictMode } from 'react'
import { createRoot } from 'react-dom/client'
import { BrowserRouter } from 'react-router-dom'
import App from './App.jsx'
import ToastProvider from './features/toast/ToastProvider.jsx'
import LikeProvider from './features/like/LikeProvider.jsx'
import './styles/tokens.css'
import './styles/base.css'
import './styles/components.css'
import './styles/layout.css'
import './styles/home.css'
import './styles/generate.css'

createRoot(document.getElementById('root')).render(
  <StrictMode>
    <BrowserRouter>
      <ToastProvider>
        <LikeProvider>
          <App />
        </LikeProvider>
      </ToastProvider>
    </BrowserRouter>
  </StrictMode>,
)
