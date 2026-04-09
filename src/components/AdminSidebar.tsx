import { LayoutDashboard, Package, Users, ShoppingBag, LogOut } from 'lucide-react';
import { useAuthStore } from '../store';

export default function AdminSidebar({ currentPath, onNavigate }: { currentPath: string, onNavigate: (path: string) => void }) {
  const { setUser } = useAuthStore();

  const handleLogout = async () => {
    await fetch('/api/auth/logout', { method: 'POST' });
    setUser(null);
    onNavigate('home');
  };

  const navItems = [
    { id: 'admin', label: 'Dashboard', icon: LayoutDashboard },
    { id: 'admin/products', label: 'Products', icon: Package },
    { id: 'admin/users', label: 'Users', icon: Users },
    { id: 'admin/orders', label: 'Orders', icon: ShoppingBag },
  ];

  return (
    <div className="w-64 h-screen fixed left-0 top-0 glass-gold border-r border-gold-600/30 flex flex-col z-40 pt-20">
      <div className="p-6">
        <h2 className="text-sm font-semibold text-gold-600 uppercase tracking-wider mb-6">Admin Panel</h2>
        <nav className="space-y-2">
          {navItems.map((item) => {
            const Icon = item.icon;
            const isActive = currentPath === item.id;
            return (
              <button
                key={item.id}
                onClick={() => onNavigate(item.id)}
                className={`w-full flex items-center gap-3 px-4 py-3 rounded-xl transition-all duration-200 ${
                  isActive 
                    ? 'bg-gold-500/10 text-gold-400 border border-gold-500/30 box-glow-gold' 
                    : 'text-gold-100/70 hover:bg-gold-900/30 hover:text-gold-100'
                }`}
              >
                <Icon className={`w-5 h-5 ${isActive ? 'text-gold-400' : ''}`} />
                <span className="font-medium">{item.label}</span>
              </button>
            );
          })}
        </nav>
      </div>
      
      <div className="mt-auto p-6 border-t border-gold-600/20">
        <button 
          onClick={handleLogout}
          className="w-full flex items-center gap-3 px-4 py-3 rounded-xl text-red-400 hover:bg-red-950/30 transition-all duration-200"
        >
          <LogOut className="w-5 h-5" />
          <span className="font-medium">Sign Out</span>
        </button>
      </div>
    </div>
  );
}
