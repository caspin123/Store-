import { useState, useEffect, useRef } from 'react';
import { Toaster } from 'react-hot-toast';
import Lenis from 'lenis';
import { useAuthStore } from './store';

// Components
import Navbar from './components/Navbar';
import CartDrawer from './components/CartDrawer';
import Hero3D from './components/Hero3D';
import ProductCard from './components/ProductCard';
import AdminSidebar from './components/AdminSidebar';

// Pages
import StorePage from './pages/StorePage';
import AdminDashboard from './pages/AdminDashboard';
import AdminUsers from './pages/AdminUsers';
import AdminProducts from './pages/AdminProducts';
import LoginPage from './pages/LoginPage';

export default function App() {
  const [currentPage, setCurrentPage] = useState('home');
  const { setUser, setLoading, isLoading } = useAuthStore();
  const cursorRef = useRef<HTMLDivElement>(null);

  // Initialize Lenis for smooth scrolling
  useEffect(() => {
    const lenis = new Lenis({
      duration: 1.2,
      easing: (t) => Math.min(1, 1.001 - Math.pow(2, -10 * t)),
      orientation: 'vertical',
      gestureOrientation: 'vertical',
      smoothWheel: true,
      wheelMultiplier: 1,
      touchMultiplier: 2,
    });

    function raf(time: number) {
      lenis.raf(time);
      requestAnimationFrame(raf);
    }

    requestAnimationFrame(raf);

    return () => {
      lenis.destroy();
    };
  }, []);

  // Custom Cursor
  useEffect(() => {
    const moveCursor = (e: MouseEvent) => {
      if (cursorRef.current) {
        cursorRef.current.style.transform = `translate3d(${e.clientX}px, ${e.clientY}px, 0)`;
      }
    };
    window.addEventListener('mousemove', moveCursor);
    return () => window.removeEventListener('mousemove', moveCursor);
  }, []);

  // Check Auth
  useEffect(() => {
    const checkAuth = async () => {
      try {
        const res = await fetch('/api/auth/me');
        if (res.ok) {
          const user = await res.json();
          setUser(user);
        } else {
          setUser(null);
        }
      } catch (error) {
        setUser(null);
      } finally {
        setLoading(false);
      }
    };
    checkAuth();
  }, [setUser, setLoading]);

  if (isLoading) {
    return (
      <div className="min-h-screen bg-background flex items-center justify-center">
        <div className="w-16 h-16 border-4 border-gold-600/30 border-t-gold-400 rounded-full animate-spin box-glow-gold"></div>
      </div>
    );
  }

  const renderPage = () => {
    switch (currentPage) {
      case 'home':
        return <HomePage onNavigate={setCurrentPage} />;
      case 'store':
        return <StorePage />;
      case 'login':
        return <LoginPage onNavigate={setCurrentPage} />;
      case 'admin':
        return <AdminDashboard />;
      case 'admin/users':
        return <AdminUsers />;
      case 'admin/products':
        return <AdminProducts />;
      default:
        return <HomePage onNavigate={setCurrentPage} />;
    }
  };

  const isAdminRoute = currentPage.startsWith('admin');

  return (
    <div className="min-h-screen bg-background text-gold-100 overflow-x-hidden selection:bg-gold-500/30 selection:text-gold-200">
      {/* Custom Cursor */}
      <div 
        ref={cursorRef}
        className="fixed top-0 left-0 w-4 h-4 bg-gold-400 rounded-full pointer-events-none z-[100] mix-blend-screen shadow-[0_0_10px_#ffd700] -translate-x-1/2 -translate-y-1/2 transition-transform duration-75 ease-out hidden md:block"
      />

      <Toaster position="bottom-right" />
      
      {!isAdminRoute && <Navbar onNavigate={setCurrentPage} />}
      <CartDrawer />

      {isAdminRoute ? (
        <div className="flex min-h-screen">
          <AdminSidebar currentPath={currentPage} onNavigate={setCurrentPage} />
          <main className="flex-1 ml-64 p-8 pt-24">
            {renderPage()}
          </main>
        </div>
      ) : (
        <main className="pt-20">
          {renderPage()}
        </main>
      )}
    </div>
  );
}

function HomePage({ onNavigate }: { onNavigate: (page: string) => void }) {
  // Mock featured products
  const featuredProducts = [
    { id: '1', name: 'Royal Gold Watch', price: 1299.99, category: 'Watches', image: 'https://images.unsplash.com/photo-1523170335258-f5ed11844a49?auto=format&fit=crop&q=80&w=800' },
    { id: '2', name: 'Diamond Encrusted Ring', price: 3499.99, category: 'Rings', image: 'https://images.unsplash.com/photo-1605100804763-247f6612223e?auto=format&fit=crop&q=80&w=800' },
    { id: '3', name: 'Golden Chain Necklace', price: 899.99, category: 'Necklaces', image: 'https://images.unsplash.com/photo-1599643478514-4a4e0f69a5a2?auto=format&fit=crop&q=80&w=800' },
  ];

  return (
    <div>
      <Hero3D />
      
      <div className="text-center -mt-10 relative z-10 mb-20">
        <h2 className="text-2xl md:text-3xl font-display text-gold-200 mb-8 text-glow-gold animate-pulse">
          Premium Products. Golden Experience.
        </h2>
        <button 
          onClick={() => onNavigate('store')}
          className="px-8 py-4 rounded-full border-2 border-gold-400 text-gold-400 font-bold uppercase tracking-widest hover:bg-gold-400 hover:text-black transition-all duration-300 box-glow-gold"
        >
          Explore Collection
        </button>
      </div>

      <section className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 py-24">
        <div className="flex items-center justify-between mb-12">
          <h2 className="text-4xl font-display text-gold-400">Featured Pieces</h2>
          <div className="h-px bg-gold-600/30 flex-1 ml-8"></div>
        </div>
        
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-8">
          {featuredProducts.map(product => (
            <ProductCard key={product.id} product={product} />
          ))}
        </div>
      </section>

      <footer className="border-t border-gold-600/20 py-12 mt-20 relative overflow-hidden">
        <div className="absolute inset-0 bg-gradient-to-b from-transparent to-gold-900/20 pointer-events-none"></div>
        <div className="max-w-7xl mx-auto px-4 text-center relative z-10">
          <h3 className="font-display text-2xl text-gold-400 mb-4">DAHABSTORE</h3>
          <p className="text-gold-100/60 mb-8">Elevating luxury to a golden standard.</p>
          <div className="flex justify-center space-x-6 text-gold-400">
            <a href="#" className="hover:text-gold-200 transition-colors">Instagram</a>
            <a href="#" className="hover:text-gold-200 transition-colors">Twitter</a>
            <a href="#" className="hover:text-gold-200 transition-colors">Pinterest</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
