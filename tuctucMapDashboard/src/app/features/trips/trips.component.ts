import { Component, OnInit, ViewChild, inject } from '@angular/core';
import { CommonModule, DatePipe } from '@angular/common';
import { MatTableDataSource, MatTableModule } from '@angular/material/table';
import { MatPaginator, MatPaginatorModule } from '@angular/material/paginator';
import { MatSort, MatSortModule } from '@angular/material/sort';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatChipsModule } from '@angular/material/chips';
import { MatButtonModule } from '@angular/material/button';
import { SupabaseService } from '../../services/supabase.service';

@Component({
  selector: 'app-trips',
  standalone: true,
  imports: [
    CommonModule,
    MatTableModule,
    MatPaginatorModule,
    MatSortModule,
    MatCardModule,
    MatIconModule,
    MatChipsModule,
    MatButtonModule,
  ],
  templateUrl: './trips.component.html',
  styleUrl: './trips.component.scss',
})
export class TripsComponent implements OnInit {
  private readonly supabaseService = inject(SupabaseService);

  displayedColumns: string[] = ['passengerName', 'driverName', 'message', 'status', 'created_at'];
  dataSource = new MatTableDataSource<any>([]);

  @ViewChild(MatPaginator) paginator!: MatPaginator;
  @ViewChild(MatSort) sort!: MatSort;

  async ngOnInit(): Promise<void> {
    await this.loadTrips();
  }

  async loadTrips(): Promise<void> {
    const trips = await this.supabaseService.getCompletedTrips();
    this.dataSource = new MatTableDataSource(trips);
    this.dataSource.paginator = this.paginator;
    this.dataSource.sort = this.sort;
  }

  exportToCsv(): void {
    const data = this.dataSource.data;
    if (!data || data.length === 0) return;

    const headers = ['ID', 'Pasajero', 'Conductor', 'Mensaje', 'Estado', 'Fecha'];
    const rows = data.map((t) => [
      `"${t.id || ''}"`,
      `"${(t.passengerName || '').replace(/"/g, '""')}"`,
      `"${(t.driverName || '').replace(/"/g, '""')}"`,
      `"${(t.message || '').replace(/"/g, '""')}"`,
      `"${t.status || ''}"`,
      `"${new Date(t.created_at).toLocaleString('es-GT')}"`,
    ]);

    const csvContent = '\uFEFF' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n');
    const blob = new Blob([csvContent], { type: 'text/csv;charset=utf-8;' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.setAttribute('href', url);
    link.setAttribute('download', `historial_viajes_${new Date().toISOString().slice(0, 10)}.csv`);
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);
  }
}
